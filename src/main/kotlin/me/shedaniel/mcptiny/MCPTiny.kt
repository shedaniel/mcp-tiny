/*
 * Copyright (c) 2019, 2020 shedaniel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:JvmName("MCPTiny")

package me.shedaniel.mcptiny

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.LinkieConfig
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.TinyExporter
import me.shedaniel.linkie.namespaces.MCPNamespace
import me.shedaniel.linkie.namespaces.MCPNamespace.loadMCPFromURLZip
import me.shedaniel.linkie.namespaces.MCPNamespace.loadTsrgFromURLZip
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.rewireIntermediaryFrom
import me.shedaniel.linkie.utils.div
import me.shedaniel.linkie.utils.info
import me.shedaniel.linkie.utils.tryToVersion
import me.shedaniel.linkie.utils.warn
import java.io.File
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun main(args: Array<String>) {
    require(args.size == 2) { "You must include two arguments: <minecraft version> <mcp snapshot>! " }
    val version = args.first().tryToVersion() ?: throw IllegalArgumentException("${args.first()} is not a valid version!")
    val mcpVersion = args[1].takeIf { arg -> arg.all { it.isDigit() } } ?: throw IllegalArgumentException("${args[1]} is not a valid mcp version! (It should be in a form of date, example: 20200916)")

    """
        ==================================================
        Please DO NOT redistribute the mappings converted.
        It may be a violation of MCP's license!
        ==================================================
    """.trimIndent().lineSequence().forEach { warn(it) }

    info("Loading in namespaces...")

    Namespaces.init(LinkieConfig.DEFAULT.copy(
        cacheDirectory = File(System.getProperty("user.dir"), "linkie-cache"),
        namespaces = listOf(MCPNamespace, YarnNamespace)
    ))
    runBlocking { delay(2000) }
    runBlocking { while (MCPNamespace.reloading || YarnNamespace.reloading) delay(100) }
    require(YarnNamespace.getAllVersions().contains(version.toString())) { "${args.first()} is not a valid version!" }
    val mcp = MappingsContainer(version.toString(), name = "MCP").apply {
        loadTsrgFromURLZip(URL("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_config/$version/mcp_config-$version.zip"))
        loadMCPFromURLZip(URL("https://files.minecraftforge.net/maven/de/oceanlabs/mcp/mcp_snapshot/$mcpVersion-$version/mcp_snapshot-$mcpVersion-$version.zip"))
        mappingSource = MappingsContainer.MappingSource.MCP_TSRG
    }
    val yarn = YarnNamespace.getProvider(version.toString()).get()

    mcp.classes.forEach {
        it.mappedName = it.intermediaryName
    }
    mcp.rewireIntermediaryFrom(yarn, true)

    info("Outputting to output.jar (overriding if exists!)")
    val path = File(System.getProperty("user.dir")) / "output.jar"
    if (path.exists()) path.delete()
    ZipOutputStream(path.outputStream()).use { zipOutputStream ->
        val zipEntry = ZipEntry("mappings/mappings.tiny")
        zipOutputStream.putNextEntry(zipEntry)

        val bytes = TinyExporter.export(mcp, "intermediary", "named").readBytes()
        zipOutputStream.write(bytes, 0, bytes.size)
        zipOutputStream.closeEntry()
    }
    info("Done!")
}
