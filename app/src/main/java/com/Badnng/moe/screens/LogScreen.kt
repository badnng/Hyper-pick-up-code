package com.Badnng.moe.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.LogEntry
import com.Badnng.moe.LogManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(modifier: Modifier = Modifier) {
    var showFilter by remember { mutableStateOf(false) }
    var selectedLevels by remember { mutableStateOf(setOf("DEBUG", "INFO", "WARN", "ERROR")) }
    var showRecognitionMonitor by remember { mutableStateOf(true) }

    val filteredLogs = LogManager.logs.filter {
        if (it.tag == "RecognitionMonitor") {
            showRecognitionMonitor
        } else {
            it.level in selectedLevels
        }
    }

    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                tonalElevation = 3.dp
            ) {
                TopAppBar(
                    title = { Text("运行日志") },
                    actions = {
                        IconButton(onClick = { showFilter = true }) {
                            Icon(Icons.Default.FilterList, null)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    windowInsets = TopAppBarDefaults.windowInsets
                )
            }
        },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 64.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 100.dp
                    )
                ) {
                    items(filteredLogs) { log ->
                        LogItem(log)
                    }
                }
            }

            if (showFilter) {
                AlertDialog(
                    onDismissRequest = { showFilter = false },
                    title = { Text("日志筛选") },
                    text = {
                        Column {
                            val levels = listOf("DEBUG", "INFO", "WARN", "ERROR")
                            levels.forEach { level ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = level in selectedLevels,
                                        onCheckedChange = {
                                            selectedLevels = if (it) selectedLevels + level else selectedLevels - level
                                        }
                                    )
                                    Text(level)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = showRecognitionMonitor,
                                    onCheckedChange = { showRecognitionMonitor = it }
                                )
                                Text("RecognitionMonitor")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showFilter = false }) { Text("确定") }
                    }
                )
            }
        }
    }
}

@Composable
fun LogItem(log: LogEntry) {
    val color = when (log.level) {
        "ERROR" -> Color(0xFFFF5252)
        "WARN" -> Color(0xFFFFB74D)
        "INFO" -> Color(0xFF81C784)
        "DEBUG" -> Color(0xFF64B5F6)
        else -> Color.White
    }

    Text(
        text = "${log.time} ${log.level.first()}/${log.tag}: ${log.message}",
        color = color,
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        lineHeight = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 0.dp)
    )
}
