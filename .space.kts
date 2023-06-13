job("publishAll") {
    startOn {
        gitPush {
//            pathFilter { + "main" + "dev" }
        }
    }
//    container("")
}