package com.jetbrains.packagesearch.plugin.maven.modifier

import com.intellij.buildsystem.model.DeclaredDependency
import com.intellij.buildsystem.model.unified.{UnifiedDependency, UnifiedDependencyRepository}
import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.{ControlFlowException, Logger}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.{module => OpenapiModule}
import com.intellij.psi.PsiManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScInfixExpr}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.sbt.language.utils.SbtDependencyCommon.defaultLibScope
import org.jetbrains.sbt.language.utils.SbtDependencyUtils.GetMode.{GetDep, GetPlace}
import org.jetbrains.sbt.language.utils.SbtDependencyUtils.{getSbtFileOpt, isScalaLibraryDependency}
import org.jetbrains.sbt.language.utils.{DependencyOrRepositoryPlaceInfo, SbtArtifactInfo, SbtDependencyCommon, SbtDependencyUtils}
import org.jetbrains.sbt.resolvers.{SbtMavenResolver, SbtResolverUtils}

import java.util
import java.util.Collections.emptyList
import scala.jdk.CollectionConverters._

class SbtDependencyModifier {
  private val logger = Logger.getInstance(this.getClass)

  def addDependency(module: OpenapiModule.Module,
                    newDependency: UnifiedDependency): Unit = {
    implicit val project: Project = module.getProject
    val sbtFileOpt = SbtDependencyUtils.getSbtFileOpt(module)
    if (sbtFileOpt == null) return
    val dependencyPlaces = inReadAction(for {
      sbtFile <- sbtFileOpt
      psiSbtFile = PsiManager.getInstance(project).findFile(sbtFile).asInstanceOf[ScalaFile]
      sbtFileModule = psiSbtFile.module.orNull
      topLevelPlace = if (sbtFileModule != null && (sbtFileModule == module || sbtFileModule.getName == s"""${module.getName}-build"""))
        Seq(SbtDependencyUtils.getTopLevelPlaceToAdd(psiSbtFile))
      else Seq.empty

      depPlaces = (SbtDependencyUtils.getLibraryDependenciesOrPlaces(sbtFileOpt, project, module, GetPlace).map(
        psiAndString => SbtDependencyUtils.toDependencyPlaceInfo(psiAndString._1, Seq()))
        ++ topLevelPlace)
        .map {
          case Some(inside: DependencyOrRepositoryPlaceInfo) => inside
          case _ => null
        }.filter(_ != null).sortWith(_.toString < _.toString)
    } yield depPlaces).getOrElse(Seq.empty)
    val newDependencyCoordinates = newDependency.getCoordinates
    val newArtifactInfo = SbtArtifactInfo(
      newDependencyCoordinates.getGroupId,
      newDependencyCoordinates.getArtifactId,
      newDependencyCoordinates.getVersion,
      newDependency.getScope
    )
    SbtDependencyUtils.addDependencyToFile()
    ApplicationManager.getApplication.invokeLater { () =>
      val wizard = new AddDependencyPreviewWizard(
        project,
        newArtifactInfo,
        dependencyPlaces)
      wizard.search() match {
        case Some(fileLine) =>
          SbtDependencyUtils.addDependency(fileLine.element, newArtifactInfo)(project)
        case _ =>
      }
    }
  }

  def updateDependency(module: OpenapiModule.Module,
                       currentDependency: UnifiedDependency,
                       newDependency: UnifiedDependency): Unit = {
    implicit val project: Project = module.getProject
    val targetedLibDepTuple = SbtDependencyUtils.findLibraryDependency(project, module, currentDependency)
    if (targetedLibDepTuple == null) return
    val oldLibDep = SbtDependencyUtils.processLibraryDependencyFromExprAndString(targetedLibDepTuple, preserve = true)
    val newCoordinates = newDependency.getCoordinates

    if (SbtDependencyUtils.cleanUpDependencyPart(oldLibDep(2).asInstanceOf[ScStringLiteral].getText) != newCoordinates.getVersion) {
      inWriteCommandAction {
        val literal = oldLibDep(2).asInstanceOf[ScStringLiteral]
        literal
          .replace(
            ScalaPsiElementFactory.createElementFromText(s""""${newCoordinates.getVersion}"""", literal)
          )
      }
      return
    }
    var oldConfiguration = ""
    if (targetedLibDepTuple._2 != "") oldConfiguration = SbtDependencyUtils.cleanUpDependencyPart(targetedLibDepTuple._2)

    if (oldLibDep.length > 3) oldConfiguration = SbtDependencyUtils.cleanUpDependencyPart(oldLibDep(3).asInstanceOf[String])
    val newConfiguration = if (newDependency.getScope != defaultLibScope) newDependency.getScope else ""
    if (oldConfiguration.toLowerCase != newConfiguration.toLowerCase) {
      if (targetedLibDepTuple._2 != "") {
        if (newConfiguration == "") {
          inWriteCommandAction(targetedLibDepTuple._3.replace(code"${targetedLibDepTuple._3.left.getText}"))
        }
        else {
          inWriteCommandAction(targetedLibDepTuple._3.right.replace(code"${newConfiguration}"))
        }

      }
      else {
        if (oldLibDep.length > 3) {
          if (newConfiguration == "") {
            inWriteCommandAction(targetedLibDepTuple._1.replace(code"${targetedLibDepTuple._1.left}"))
          }
          else {
            inWriteCommandAction(targetedLibDepTuple._1.right.replace(code"""${newConfiguration}"""))
          }
        }
        else {
          if (newConfiguration != "") {
            inWriteCommandAction(targetedLibDepTuple._1.replace(code"""${targetedLibDepTuple._1.getText} % $newConfiguration"""))
          }
        }
      }
    }
  }


  def removeDependency(module: OpenapiModule.Module, toRemoveDependency: UnifiedDependency): Unit = {
    implicit val project: Project = module.getProject
    val targetedLibDepTuple = SbtDependencyUtils.findLibraryDependency(project, module, toRemoveDependency)
    targetedLibDepTuple._3.getParent match {
      case _: ScArgumentExprList => inWriteCommandAction {
        targetedLibDepTuple._3.delete()
      }
      case infix: ScInfixExpr if infix.left.textMatches(SbtDependencyUtils.LIBRARY_DEPENDENCIES) => inWriteCommandAction {
        infix.delete()
      }
      case _ =>
    }
  }

  def addRepository(module: OpenapiModule.Module,
                    unifiedDependencyRepository: UnifiedDependencyRepository): Unit = {
    implicit val project: Project = module.getProject
    val sbtFileOpt = SbtDependencyUtils.getSbtFileOpt(module)
    if (sbtFileOpt == null) return
    val sbtFile = sbtFileOpt.orNull
    if (sbtFile == null) return
    val psiSbtFile = PsiManager.getInstance(project).findFile(sbtFile).asInstanceOf[ScalaFile]

    SbtDependencyUtils.addRepository(psiSbtFile, unifiedDependencyRepository)
  }

  def deleteRepository(module: OpenapiModule.Module,
                       unifiedDependencyRepository: UnifiedDependencyRepository): Unit = {}

  def declaredDependencies(module: OpenapiModule.Module): util.List[DeclaredDependency] = try {

    // Check whether the IDE is in Dumb Mode. If it is, return empty list instead proceeding
    if (DumbService.getInstance(module.getProject).isDumb) return emptyList()

    val libDeps = SbtDependencyUtils.
      getLibraryDependenciesOrPlaces(getSbtFileOpt(module), module.getProject, module, GetDep).
      map(_.asInstanceOf[(ScInfixExpr, String, ScInfixExpr)])

    val scalaVer = module.scalaMinorVersion.fold("")(_.minor)

    inReadAction({
      libDeps.map(libDepInfixAndString => {
        val libDepArr = SbtDependencyUtils.processLibraryDependencyFromExprAndString(libDepInfixAndString).map(_.asInstanceOf[String])
        val dataContext: DataContext = (dataId: String) => {
          if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
            libDepInfixAndString
          } else null
        }

        libDepArr.length match {
          case x if x < 3 || x > 4 => null
          case x if x >= 3 =>
            val scope = if (x == 3) SbtDependencyCommon.defaultLibScope else libDepArr(3)
            if (isScalaLibraryDependency(libDepInfixAndString._1))
              new DeclaredDependency(
                new UnifiedDependency(
                  libDepArr(0),
                  SbtDependencyUtils.buildScalaArtifactIdString(libDepArr(0), libDepArr(1), scalaVer),
                  libDepArr(2),
                  scope),
                dataContext)
            else
              new DeclaredDependency(
                new UnifiedDependency(
                  libDepArr(0),
                  libDepArr(1),
                  libDepArr(2),
                  scope),
                dataContext)
        }
      }).filter(_ != null).toList.asJava
    })
  } catch {
    case c: ControlFlowException => throw c
    case e: Exception =>
      logger.error(s"Error occurs when obtaining the list of dependencies for module ${module.getName} using package search plugin", e)
      emptyList()
  }

  def declaredRepositories(module: OpenapiModule.Module): util.List[UnifiedDependencyRepository] = try {
    SbtResolverUtils.projectResolvers(module.getProject).collect {
      case r: SbtMavenResolver =>
        new UnifiedDependencyRepository(r.name, r.presentableName, r.normalizedRoot)
    }.toList.asJava
  } catch {
    case c: ControlFlowException => throw c
    case e: Exception =>
      logger.error(s"Error occurs when obtaining the list of supported repositories/resolvers for module ${module.getName} using package search plugin", e)
      emptyList()
  }
}
