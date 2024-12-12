package com.zepben.profilemanager

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.zepben.profilemanager.commands.Generate
import com.zepben.profilemanager.commands.Import
import com.zepben.profilemanager.commands.Patch

class App : CliktCommand() {

    override fun run() {
    }
}

fun main(args: Array<String>) = App().subcommands(Import(), Generate(), Patch()).main(args)
