@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.Badnng.moe.ui.component

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add as Md3eAdd
import androidx.compose.material.icons.filled.Close as Md3eClose
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication

@Stable
class CustomPickupLocationsEditorState internal constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var locations by mutableStateOf(load())
        private set

    var input by mutableStateOf("")

    fun addInput(): AddCustomLocationResult {
        val candidate = input.trim()
        if (candidate.isEmpty()) {
            input = ""
            return AddCustomLocationResult.Empty
        }
        if (locations.any { it.equals(candidate, ignoreCase = true) }) {
            return AddCustomLocationResult.Duplicate
        }
        if (locations.size >= MAX_LOCATIONS) {
            return AddCustomLocationResult.LimitReached
        }
        locations = save(locations + candidate)
        input = ""
        return AddCustomLocationResult.Added
    }

    fun remove(location: String) {
        locations = save(locations.filterNot { it == location })
    }

    private fun load(): List<String> =
        prefs.getString("custom_pickup_locations", "")
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.distinct()
            ?: emptyList()

    private fun save(newList: List<String>): List<String> {
        prefs.edit()
            .putString("custom_pickup_locations", newList.joinToString(","))
            .apply()
        return newList
    }

    companion object {
        const val MAX_LOCATIONS = 50
    }
}

enum class AddCustomLocationResult {
    Added,
    Empty,
    Duplicate,
    LimitReached,
}

@Composable
fun rememberCustomPickupLocationsEditorState(): CustomPickupLocationsEditorState {
    val context = LocalContext.current
    return remember(context) { CustomPickupLocationsEditorState(context) }
}

@Composable
fun Md3eCustomPickupLocationsEditor(
    state: CustomPickupLocationsEditorState,
    performHaptic: () -> Unit,
) {
    val context = LocalContext.current
    val addLocation = {
        performHaptic()
        showAddResult(context, state.addInput())
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CustomLocationsEmptyOrFlow(
            locations = state.locations,
            item = { location ->
                InputChip(
                    selected = false,
                    onClick = {
                        performHaptic()
                        state.remove(location)
                    },
                    label = {
                        Text(
                            text = location,
                            modifier = Modifier.widthIn(max = 240.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Md3eClose,
                            contentDescription = "删除取件地点 $location",
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    shape = RoundedCornerShape(15.dp),
                )
            },
        )
        Column {
            OutlinedTextField(
                value = state.input,
                onValueChange = { state.input = it.replace("\n", "") },
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            addLocation()
                            true
                        } else {
                            false
                        }
                    },
                label = { Text("添加取件地点") },
                supportingText = {
                    Text("${state.locations.size}/${CustomPickupLocationsEditorState.MAX_LOCATIONS}")
                },
                trailingIcon = {
                    IconButton(onClick = addLocation) {
                        Icon(Icons.Default.Md3eAdd, contentDescription = "添加取件地点")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addLocation() }),
                shape = RoundedCornerShape(15.dp),
            )
            Text(
                text = "添加取件地点关键词，识别文本中包含这些关键词时直接将其作为取件地点。点击标签可删除。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun MiuixCustomPickupLocationsEditor(
    state: CustomPickupLocationsEditorState,
    performHaptic: () -> Unit,
) {
    val context = LocalContext.current
    val addLocation = {
        performHaptic()
        showAddResult(context, state.addInput())
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CustomLocationsEmptyOrFlow(
            locations = state.locations,
            item = { location ->
                MiuixCustomLocationChip(
                    location = location,
                    onRemove = {
                        performHaptic()
                        state.remove(location)
                    },
                )
            },
        )
        Column {
            MiuixTextField(
                value = state.input,
                onValueChange = { state.input = it.replace("\n", "") },
                label = "添加取件地点（${state.locations.size}/${CustomPickupLocationsEditorState.MAX_LOCATIONS}）",
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            addLocation()
                            true
                        } else {
                            false
                        }
                    },
                trailingIcon = {
                    MiuixIconButton(onClick = addLocation) {
                        MiuixIcon(MiuixIcons.Regular.Add, contentDescription = "添加取件地点")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addLocation() }),
            )
            MiuixText(
                text = "添加取件地点关键词，识别文本中包含这些关键词时直接将其作为取件地点。点击标签可删除。",
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun CustomLocationsEmptyOrFlow(
    locations: List<String>,
    item: @Composable (String) -> Unit,
) {
    if (locations.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            locations.forEach { location -> item(location) }
        }
    }
}

@Composable
private fun MiuixCustomLocationChip(
    location: String,
    onRemove: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val indicationColor = MiuixTheme.colorScheme.onSurface
    val indication = remember(indicationColor) { MiuixIndication(color = indicationColor) }

    Row(
        modifier = Modifier
            .squircleSurface(MiuixTheme.colorScheme.surfaceContainer, 15.dp)
            .squircleClip(15.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = indication,
                role = Role.Button,
                onClick = onRemove,
            )
            .semantics {
                role = Role.Button
                contentDescription = "删除取件地点 $location"
            }
            .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiuixText(
            text = location,
            modifier = Modifier.widthIn(max = 240.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        MiuixIcon(
            imageVector = MiuixIcons.Regular.Close,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun showAddResult(context: Context, result: AddCustomLocationResult) {
    val message = when (result) {
        AddCustomLocationResult.Added,
        AddCustomLocationResult.Empty -> return
        AddCustomLocationResult.Duplicate -> "该取件地点关键词已存在"
        AddCustomLocationResult.LimitReached ->
            "最多添加 ${CustomPickupLocationsEditorState.MAX_LOCATIONS} 条取件地点关键词"
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}