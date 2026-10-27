package com.greenaltambe.vela.data

enum class DownloadState { QUEUED, DOWNLOADING, PAUSED, MERGING, COMPLETED, FAILED }

enum class DownloadKind { VIDEO, AUDIO_ONLY }
