package com.greenaltambe.vela.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscriptionImportTest {
    private val a = "UC" + "a".repeat(22)
    private val b = "UC" + "b".repeat(22)

    @Test
    fun parsesTakeoutCsvWithQuotedTitles() {
        val csv = "Channel Id,Channel Url,Channel Title\r\n$a,http://www.youtube.com/channel/$a,\"Cats, Dogs & \"\"Co\"\"\"\r\n$b,http://www.youtube.com/channel/$b,Plain\r\n"
        val result = parseTakeoutCsv(csv)
        assertEquals(listOf(ImportedChannel(a, "Cats, Dogs & \"Co\""), ImportedChannel(b, "Plain")), result.channels)
        assertEquals(0, result.skipped)
    }

    @Test
    fun csvSkipsRowsWithoutAUsableIdAndDuplicates() {
        val csv = "﻿Channel Id,Channel Url,Channel Title\nnot-an-id,x,Bad\n$a,u,One\n$a,u,One again\n\n"
        val result = parseTakeoutCsv(csv)
        assertEquals(listOf(a), result.channels.map { it.channelId })
        assertEquals(1, result.skipped)
    }

    @Test
    fun emptyCsvImportsNothing() {
        assertTrue(parseTakeoutCsv("").channels.isEmpty())
        assertTrue(parseTakeoutCsv("Channel Id,Channel Url,Channel Title\n").channels.isEmpty())
    }

    @Test
    fun parsesNewPipeExport() {
        val json = """{"app_version":"0.27","subscriptions":[
            {"service_id":0,"url":"https://www.youtube.com/channel/$a","name":"One"},
            {"service_id":1,"url":"https://soundcloud.com/x","name":"Other service"},
            {"service_id":0,"url":"https://www.youtube.com/@handle","name":"No id"},
            {"service_id":0,"url":"https://www.youtube.com/channel/$b","name":""}]}"""
        val result = parseNewPipeJson(json)
        assertEquals(listOf(ImportedChannel(a, "One"), ImportedChannel(b, b)), result.channels)
        assertEquals(2, result.skipped)
    }

    @Test
    fun brokenJsonIsEmptyNotACrash() {
        assertTrue(parseNewPipeJson("{not json").channels.isEmpty())
        assertTrue(parseNewPipeJson("""{"other":1}""").channels.isEmpty())
    }

    @Test
    fun fileTypeIsDetectedByContent() {
        assertEquals(1, parseSubscriptionFile("""{"subscriptions":[{"service_id":0,"url":"https://www.youtube.com/channel/$a","name":"x"}]}""").channels.size)
        assertEquals(1, parseSubscriptionFile("Channel Id,Channel Url,Channel Title\n$a,u,x").channels.size)
    }
}
