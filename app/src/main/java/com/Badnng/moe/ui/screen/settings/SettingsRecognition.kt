@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.Badnng.moe.ui.screen.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess as Md3eExpandLess
import androidx.compose.material.icons.filled.ExpandMore as Md3eExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Badnng.moe.recognition.CustomRequestMode
import com.Badnng.moe.recognition.MimoBillingMode
import com.Badnng.moe.recognition.OnlineRecognitionClient
import com.Badnng.moe.recognition.OnlineRecognitionCatalog
import com.Badnng.moe.recognition.OnlineRecognitionModel
import com.Badnng.moe.recognition.OnlineRecognitionPreferences
import com.Badnng.moe.recognition.OnlineRecognitionProvider
import com.Badnng.moe.recognition.SecureApiKeyStore
import com.Badnng.moe.privacy.PrivacyConsent
import com.Badnng.moe.ui.component.OnlineRecognitionProviderIcon
import com.Badnng.moe.ui.component.PrivacyConsentBottomSheet
import com.Badnng.moe.ui.miuix.MiuixSettingsLazyColumn
import com.Badnng.moe.ui.miuix.rememberMiuixStyle
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as MiuixTextFieldDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Hide
import top.yukonga.miuix.kmp.icon.extended.Show
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RecognitionSettingsContent(
    performHaptic: () -> Unit,
    topPadding: Dp = 0.dp,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    onNavigateToPromptEditor: () -> Unit = {},
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val preferences = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val keyStore = remember { SecureApiKeyStore(context) }
    val clipboardManager = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    val isMiuix = rememberMiuixStyle()

    var recognitionMode by remember {
        val savedMode = preferences.getString(
            OnlineRecognitionPreferences.MODE_KEY,
            OnlineRecognitionPreferences.MODE_OFFLINE,
        ) ?: OnlineRecognitionPreferences.MODE_OFFLINE
        mutableStateOf(
            if (savedMode == OnlineRecognitionPreferences.MODE_ONLINE &&
                !PrivacyConsent.isCurrentPolicyAccepted(preferences)
            ) {
                OnlineRecognitionPreferences.MODE_OFFLINE
            } else {
                savedMode
            },
        )
    }
    var provider by remember {
        mutableStateOf(OnlineRecognitionPreferences.provider(context))
    }
    var model by remember {
        mutableStateOf(OnlineRecognitionPreferences.model(context, provider))
    }
    var mimoBillingMode by remember {
        mutableStateOf(OnlineRecognitionPreferences.mimoBillingMode(context))
    }
    var customRequestMode by remember {
        mutableStateOf(OnlineRecognitionPreferences.customRequestMode(context))
    }
    var customBaseUrl by remember {
        val savedUrl = OnlineRecognitionPreferences.customBaseUrl(context)
        mutableStateOf(TextFieldValue(savedUrl, TextRange(savedUrl.length)))
    }
    var customModels by remember { mutableStateOf(emptyList<OnlineRecognitionModel>()) }
    var customModelsLoading by remember { mutableStateOf(false) }
    var customModelsError by remember { mutableStateOf<String?>(null) }
    var apiKeyInput by remember(provider) {
        val savedKey = keyStore.get(provider).orEmpty()
        mutableStateOf(TextFieldValue(savedKey, TextRange(savedKey.length)))
    }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(preferences) {
        if (!PrivacyConsent.isCurrentPolicyAccepted(preferences) &&
            preferences.getString(
                OnlineRecognitionPreferences.MODE_KEY,
                OnlineRecognitionPreferences.MODE_OFFLINE,
            ) == OnlineRecognitionPreferences.MODE_ONLINE
        ) {
            preferences.edit()
                .putString(
                    OnlineRecognitionPreferences.MODE_KEY,
                    OnlineRecognitionPreferences.MODE_OFFLINE,
                )
                .apply()
        }
    }

    LaunchedEffect(provider) {
        model = OnlineRecognitionPreferences.model(context, provider)
        val savedKey = keyStore.get(provider).orEmpty()
        apiKeyInput = TextFieldValue(savedKey, TextRange(savedKey.length))
        apiKeyVisible = false
    }

    LaunchedEffect(provider, customBaseUrl.text, apiKeyInput.text) {
        if (provider != OnlineRecognitionProvider.CUSTOM) return@LaunchedEffect
        customModels = emptyList()
        customModelsError = null
        if (customBaseUrl.text.isBlank() || apiKeyInput.text.isBlank()) return@LaunchedEffect
        delay(700)
        customModelsLoading = true
        runCatching {
            OnlineRecognitionClient(context).fetchCustomModels(customBaseUrl.text)
        }.onSuccess { fetchedModels ->
            customModels = fetchedModels
            val savedModel = OnlineRecognitionPreferences.model(context, provider)
            val selectedModel = fetchedModels.firstOrNull { it.id == savedModel.id }
                ?: fetchedModels.first()
            model = selectedModel
            OnlineRecognitionPreferences.saveModel(context, provider, selectedModel.id)
        }.onFailure { error ->
            customModelsError = error.message ?: "获取模型失败"
        }
        customModelsLoading = false
    }

    fun setMode(mode: String) {
        performHaptic()
        if (mode == OnlineRecognitionPreferences.MODE_ONLINE &&
            !PrivacyConsent.isCurrentPolicyAccepted(preferences)
        ) {
            showPrivacyDialog = true
            return
        }
        recognitionMode = mode
        preferences.edit().putString(OnlineRecognitionPreferences.MODE_KEY, mode).apply()
    }

    fun setProvider(newProvider: OnlineRecognitionProvider) {
        performHaptic()
        provider = newProvider
        preferences.edit()
            .putString(OnlineRecognitionPreferences.PROVIDER_KEY, newProvider.key)
            .apply()
    }

    fun setModel(newModel: OnlineRecognitionModel) {
        performHaptic()
        model = newModel
        OnlineRecognitionPreferences.saveModel(context, provider, newModel.id)
    }

    fun updateApiKey(value: TextFieldValue) {
        apiKeyInput = value
        keyStore.save(provider, value.text)
    }

    fun pasteApiKey() {
        val clip = clipboardManager.primaryClip ?: return
        if (clip.itemCount == 0) return
        val pastedText = clip.getItemAt(0).coerceToText(context)?.toString() ?: return
        val selectionStart = apiKeyInput.selection.min.coerceIn(0, apiKeyInput.text.length)
        val selectionEnd = apiKeyInput.selection.max.coerceIn(0, apiKeyInput.text.length)
        val updatedText = apiKeyInput.text.replaceRange(selectionStart, selectionEnd, pastedText)
        updateApiKey(
            TextFieldValue(
                text = updatedText,
                selection = TextRange(selectionStart + pastedText.length),
            )
        )
    }

    val clearFocusModifier = Modifier.pointerInput(focusManager) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val up = waitForUpOrCancellation(PointerEventPass.Final)
                    if (up != null && !down.isConsumed && !up.isConsumed) {
                        focusManager.clearFocus()
                    }
                }
            }

    if (isMiuix) {
        val sections = MiuixRecognitionSettingsSections(
                recognitionMode = recognitionMode,
                provider = provider,
                model = model,
                mimoBillingMode = mimoBillingMode,
                customRequestMode = customRequestMode,
                customBaseUrl = customBaseUrl,
                customModels = customModels,
                apiKeyInput = apiKeyInput,
                apiKeyVisible = apiKeyVisible,
                onModeSelected = ::setMode,
                onProviderSelected = ::setProvider,
                onModelSelected = ::setModel,
                onMimoBillingModeSelected = { selected ->
                    performHaptic()
                    mimoBillingMode = selected
                    preferences.edit()
                        .putString(OnlineRecognitionPreferences.MIMO_BILLING_KEY, selected.key)
                        .apply()
                },
                onCustomRequestModeSelected = { selected ->
                    performHaptic()
                    customRequestMode = selected
                    preferences.edit()
                        .putString(OnlineRecognitionPreferences.CUSTOM_REQUEST_MODE_KEY, selected.key)
                        .apply()
                },
                onCustomBaseUrlChange = { value ->
                    customBaseUrl = value
                    preferences.edit()
                        .putString(OnlineRecognitionPreferences.CUSTOM_BASE_URL_KEY, value.text.trim())
                        .apply()
                },
                onApiKeyChange = ::updateApiKey,
                onApiKeyVisibilityChange = { apiKeyVisible = !apiKeyVisible },
                canPasteApiKey = clipboardManager::hasPrimaryClip,
                onPasteApiKey = ::pasteApiKey,
                performHaptic = performHaptic,
                onNavigateToPromptEditor = onNavigateToPromptEditor,
            )
        MiuixSettingsLazyColumn(
            sections = sections,
            contentPadding = PaddingValues(top = topPadding, bottom = 32.dp),
            modifier = clearFocusModifier,
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .then(clearFocusModifier)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(topPadding))
            Md3eRecognitionSettings(
                recognitionMode = recognitionMode,
                provider = provider,
                model = model,
                mimoBillingMode = mimoBillingMode,
                customRequestMode = customRequestMode,
                customBaseUrl = customBaseUrl,
                customModels = customModels,
                customModelsLoading = customModelsLoading,
                customModelsError = customModelsError,
                apiKeyInput = apiKeyInput,
                apiKeyVisible = apiKeyVisible,
                onModeSelected = ::setMode,
                onProviderSelected = ::setProvider,
                onModelSelected = ::setModel,
                onMimoBillingModeSelected = { selected ->
                    performHaptic()
                    mimoBillingMode = selected
                    preferences.edit()
                        .putString(OnlineRecognitionPreferences.MIMO_BILLING_KEY, selected.key)
                        .apply()
                },
                onCustomRequestModeSelected = { selected ->
                    performHaptic()
                    customRequestMode = selected
                    preferences.edit()
                        .putString(OnlineRecognitionPreferences.CUSTOM_REQUEST_MODE_KEY, selected.key)
                        .apply()
                },
                onCustomBaseUrlChange = { value ->
                    customBaseUrl = value
                    preferences.edit()
                        .putString(OnlineRecognitionPreferences.CUSTOM_BASE_URL_KEY, value.text.trim())
                        .apply()
                },
                onApiKeyChange = ::updateApiKey,
                onApiKeyVisibilityChange = { apiKeyVisible = !apiKeyVisible },
                canPasteApiKey = clipboardManager::hasPrimaryClip,
                onPasteApiKey = ::pasteApiKey,
                performHaptic = performHaptic,
                onNavigateToPromptEditor = onNavigateToPromptEditor,
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    PrivacyConsentBottomSheet(
        show = showPrivacyDialog,
        isMiuix = isMiuix,
        title = "启用在线识别",
        onDismiss = {
            performHaptic()
            showPrivacyDialog = false
        },
        onConfirm = {
            performHaptic()
            PrivacyConsent.accept(preferences)
            preferences.edit()
                .putString(
                    OnlineRecognitionPreferences.MODE_KEY,
                    OnlineRecognitionPreferences.MODE_ONLINE,
                )
                .apply()
            recognitionMode = OnlineRecognitionPreferences.MODE_ONLINE
            showPrivacyDialog = false
        },
    )
}

@Composable
private fun MiuixRecognitionSettingsSections(
    recognitionMode: String,
    provider: OnlineRecognitionProvider,
    model: OnlineRecognitionModel,
    mimoBillingMode: MimoBillingMode,
    customRequestMode: CustomRequestMode,
    customBaseUrl: TextFieldValue,
    customModels: List<OnlineRecognitionModel>,
    apiKeyInput: TextFieldValue,
    apiKeyVisible: Boolean,
    onModeSelected: (String) -> Unit,
    onProviderSelected: (OnlineRecognitionProvider) -> Unit,
    onModelSelected: (OnlineRecognitionModel) -> Unit,
    onMimoBillingModeSelected: (MimoBillingMode) -> Unit,
    onCustomRequestModeSelected: (CustomRequestMode) -> Unit,
    onCustomBaseUrlChange: (TextFieldValue) -> Unit,
    onApiKeyChange: (TextFieldValue) -> Unit,
    onApiKeyVisibilityChange: () -> Unit,
    canPasteApiKey: () -> Boolean,
    onPasteApiKey: () -> Unit,
    performHaptic: () -> Unit,
    onNavigateToPromptEditor: () -> Unit,
): List<@Composable () -> Unit> = buildList {
    add {
        SmallTitle("识别方式")
        MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
            OverlayDropdownPreference(
                title = "识别方式",
                entries = listOf(DropdownEntry(
                    items = listOf(
                        DropdownItem(
                            text = "离线识别",
                            selected = recognitionMode == OnlineRecognitionPreferences.MODE_OFFLINE,
                            onClick = { onModeSelected(OnlineRecognitionPreferences.MODE_OFFLINE) },
                        ),
                        DropdownItem(
                            text = "在线识别",
                            selected = recognitionMode == OnlineRecognitionPreferences.MODE_ONLINE,
                            onClick = { onModeSelected(OnlineRecognitionPreferences.MODE_ONLINE) },
                        ),
                    )
                )),
            )
        }
    }

    add {
        if (recognitionMode == OnlineRecognitionPreferences.MODE_ONLINE) {
            SmallTitle("在线识别服务")
            MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                MiuixProviderDropdown(
                    provider = provider,
                    onSelected = onProviderSelected,
                )
                if (provider == OnlineRecognitionProvider.CUSTOM) {
                    OverlayDropdownPreference(
                        title = "请求模式",
                        entries = listOf(DropdownEntry(
                            items = CustomRequestMode.entries.map { item ->
                                DropdownItem(
                                    text = item.displayName,
                                    selected = customRequestMode == item,
                                    onClick = { onCustomRequestModeSelected(item) },
                                )
                            }
                        )),
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MiuixTextField(
                            value = customBaseUrl,
                            onValueChange = onCustomBaseUrlChange,
                            label = "API 请求地址",
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        )
                        MiuixText(
                            text = "请填写到 /responses 或 /chat/completions 前的 API 请求地址。",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        MiuixText(
                            text = CUSTOM_PROVIDER_WARNING,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        MiuixText(
                            text = CUSTOM_MODEL_RECOMMENDATION,
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    OverlayDropdownPreference(
                        title = "模型",
                        entries = listOf(DropdownEntry(
                            items = customModels.map { item ->
                                DropdownItem(
                                    text = item.displayName,
                                    selected = model.id == item.id,
                                    onClick = { onModelSelected(item) },
                                )
                            }
                        )),
                    )
                } else {
                    OverlayDropdownPreference(
                        title = "模型",
                        entries = listOf(DropdownEntry(
                            items = OnlineRecognitionCatalog.modelsFor(provider).map { item ->
                                DropdownItem(
                                    text = item.displayName,
                                    selected = model.id == item.id,
                                    onClick = { onModelSelected(item) },
                                )
                            }
                        )),
                    )
                }
            }
        }
    }

    add {
        if (recognitionMode == OnlineRecognitionPreferences.MODE_ONLINE) {
            SmallTitle("识别提示词")
            MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                ArrowPreference(
                    title = "自定义 Prompt",
                    summary = "编辑在线识别使用的系统提示词",
                    onClick = {
                        performHaptic()
                        onNavigateToPromptEditor()
                    },
                )
            }
        }
    }

    add {
        if (recognitionMode == OnlineRecognitionPreferences.MODE_ONLINE &&
            provider == OnlineRecognitionProvider.MIMO
        ) {
                SmallTitle("计费模式")
                MiuixCard(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    MimoBillingMode.entries.forEach { item ->
                        RadioButtonPreference(
                            title = item.displayName,
                            summary = if (item == MimoBillingMode.PAY_AS_YOU_GO) {
                                "api.xiaomimimo.com"
                            } else {
                                "token-plan-cn.xiaomimimo.com"
                            },
                            selected = mimoBillingMode == item,
                            onClick = { onMimoBillingModeSelected(item) },
                            radioButtonLocation = RadioButtonLocation.End,
                        )
                    }
                }
        }
    }

    add {
        if (recognitionMode == OnlineRecognitionPreferences.MODE_ONLINE) {
            SmallTitle("API 密钥")
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    MiuixTextField(
                        value = apiKeyInput,
                        onValueChange = onApiKeyChange,
                        label = "${provider.displayName} API 密钥",
                        modifier = Modifier
                            .fillMaxWidth()
                            .apiKeyPasteContextMenu(canPasteApiKey, onPasteApiKey),
                        colors = MiuixTextFieldDefaults.textFieldColors(
                            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
                            borderColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        visualTransformation = if (apiKeyVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            MiuixIconButton(onClick = onApiKeyVisibilityChange) {
                                MiuixIcon(
                                    imageVector = if (apiKeyVisible) {
                                        MiuixIcons.Regular.Hide
                                    } else {
                                        MiuixIcons.Regular.Show
                                    },
                                    contentDescription = if (apiKeyVisible) "隐藏密钥" else "显示密钥",
                                )
                            }
                        },
                    )
                    MiuixText(
                        text = "密钥由 Android Keystore 加密，仅保存在当前设备",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }

    add {
        if (recognitionMode == OnlineRecognitionPreferences.MODE_ONLINE &&
            (provider == OnlineRecognitionProvider.MIMO ||
                provider == OnlineRecognitionProvider.ZHIPU ||
                provider == OnlineRecognitionProvider.MINIMAX ||
                provider == OnlineRecognitionProvider.MOONSHOT ||
                provider == OnlineRecognitionProvider.OPENCODE_GO ||
                provider == OnlineRecognitionProvider.OPENCODE_ZEN ||
                provider == OnlineRecognitionProvider.DEEPSEEK)
        ) {
            SmallTitle("使用说明")
            ProviderUsageGuide(
                provider = provider,
                isMiuix = true,
                performHaptic = performHaptic,
            )
        }
    }
}

@Composable
private fun MiuixProviderDropdown(
    provider: OnlineRecognitionProvider,
    onSelected: (OnlineRecognitionProvider) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        OverlayDropdownPreference(
            title = "供应商",
            entries = listOf(
                DropdownEntry(
                    items = OnlineRecognitionProvider.entries.map { item ->
                        DropdownItem(
                            text = item.displayName,
                            selected = provider == item,
                            onClick = { onSelected(item) },
                            icon = { _ ->
                                Box(
                                    modifier = Modifier.size(width = 38.dp, height = 28.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    OnlineRecognitionProviderIcon(
                                        provider = item,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            },
                        )
                    },
                ),
            ),
            showValue = false,
        )
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OnlineRecognitionProviderIcon(
                provider = provider,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(8.dp))
            MiuixText(
                text = provider.displayName,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Md3eRecognitionSettings(
    recognitionMode: String,
    provider: OnlineRecognitionProvider,
    model: OnlineRecognitionModel,
    mimoBillingMode: MimoBillingMode,
    customRequestMode: CustomRequestMode,
    customBaseUrl: TextFieldValue,
    customModels: List<OnlineRecognitionModel>,
    customModelsLoading: Boolean,
    customModelsError: String?,
    apiKeyInput: TextFieldValue,
    apiKeyVisible: Boolean,
    onModeSelected: (String) -> Unit,
    onProviderSelected: (OnlineRecognitionProvider) -> Unit,
    onModelSelected: (OnlineRecognitionModel) -> Unit,
    onMimoBillingModeSelected: (MimoBillingMode) -> Unit,
    onCustomRequestModeSelected: (CustomRequestMode) -> Unit,
    onCustomBaseUrlChange: (TextFieldValue) -> Unit,
    onApiKeyChange: (TextFieldValue) -> Unit,
    onApiKeyVisibilityChange: () -> Unit,
    canPasteApiKey: () -> Boolean,
    onPasteApiKey: () -> Unit,
    performHaptic: () -> Unit,
    onNavigateToPromptEditor: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Md3eSection("识别方式") {
            SettingsCard {
                Md3eDropdown(
                    title = "识别方式",
                    selectedText = if (recognitionMode == OnlineRecognitionPreferences.MODE_ONLINE) {
                        "在线识别"
                    } else {
                        "离线识别"
                    },
                    options = listOf(
                        OnlineRecognitionPreferences.MODE_OFFLINE to "离线识别",
                        OnlineRecognitionPreferences.MODE_ONLINE to "在线识别",
                    ),
                    onSelected = { onModeSelected(it) },
                )
            }
        }

        if (recognitionMode == OnlineRecognitionPreferences.MODE_ONLINE) {
            Md3eSection("在线识别服务") {
                SettingsCard {
                    Md3eProviderDropdown(provider, onProviderSelected)
                    Spacer(Modifier.height(12.dp))
                    if (provider == OnlineRecognitionProvider.CUSTOM) {
                        Md3eDropdown(
                            title = "请求模式",
                            selectedText = customRequestMode.displayName,
                            options = CustomRequestMode.entries.map { it.key to it.displayName },
                            onSelected = { key ->
                                onCustomRequestModeSelected(CustomRequestMode.fromKey(key))
                            },
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = customBaseUrl,
                            onValueChange = onCustomBaseUrlChange,
                            label = { Text("API 请求地址") },
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                Text("请填写到 /responses 或 /chat/completions 前的 API 请求地址。")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            shape = RoundedCornerShape(15.dp),
                        )
                        Text(
                            text = CUSTOM_PROVIDER_WARNING,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = CUSTOM_MODEL_RECOMMENDATION,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Md3eModelDropdown(
                            model = model,
                            models = customModels,
                            summary = customModelSummary(
                                model,
                                customModels,
                                customModelsLoading,
                                customModelsError,
                            ),
                            onSelected = onModelSelected,
                        )
                    } else {
                        Md3eModelDropdown(
                            model = model,
                            models = OnlineRecognitionCatalog.modelsFor(provider),
                            onSelected = onModelSelected,
                        )
                    }
                }
            }

            Md3eSection("识别提示词") {
                SettingsCard {
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                performHaptic()
                                onNavigateToPromptEditor()
                            },
                        headlineContent = { Text("自定义 Prompt") },
                        supportingContent = { Text("编辑在线识别使用的系统提示词") },
                        trailingContent = {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            if (provider == OnlineRecognitionProvider.MIMO) {
                Md3eSection("计费模式") {
                    SettingsCard(contentPadding = PaddingValues(vertical = 4.dp)) {
                        MimoBillingMode.entries.forEach { item ->
                            val selected = mimoBillingMode == item
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onMimoBillingModeSelected(item) },
                                leadingContent = {
                                    RadioButton(
                                        selected = selected,
                                        onClick = null,
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        text = item.displayName,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = if (item == MimoBillingMode.PAY_AS_YOU_GO) {
                                            "api.xiaomimimo.com"
                                        } else {
                                            "token-plan-cn.xiaomimimo.com"
                                        },
                                    )
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    } else {
                                        Color.Transparent
                                    },
                                    headlineColor = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    supportingColor = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                ),
                            )
                        }
                    }
                }
            }

            Md3eSection("API 密钥") {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = onApiKeyChange,
                    label = { Text("${provider.displayName} API 密钥") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .apiKeyPasteContextMenu(canPasteApiKey, onPasteApiKey),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    visualTransformation = if (apiKeyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = onApiKeyVisibilityChange) {
                            Icon(
                                imageVector = if (apiKeyVisible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (apiKeyVisible) "隐藏密钥" else "显示密钥",
                            )
                        }
                    },
                    supportingText = {
                        Text("密钥由 Android Keystore 加密，仅保存在当前设备")
                    },
                    shape = RoundedCornerShape(15.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }

            if (provider == OnlineRecognitionProvider.MIMO ||
                provider == OnlineRecognitionProvider.ZHIPU ||
                provider == OnlineRecognitionProvider.MINIMAX ||
                provider == OnlineRecognitionProvider.MOONSHOT ||
                provider == OnlineRecognitionProvider.OPENCODE_GO ||
                provider == OnlineRecognitionProvider.OPENCODE_ZEN ||
                provider == OnlineRecognitionProvider.DEEPSEEK
            ) {
                Md3eSection("使用说明") {
                    ProviderUsageGuide(
                        provider = provider,
                        isMiuix = false,
                        performHaptic = performHaptic,
                    )
                }
            }
        }
    }
}

private data class ProviderGuideStep(
    val text: String,
    val linkLabel: String? = null,
    val link: String? = null,
)

private data class ProviderGuideSection(
    val title: String? = null,
    val steps: List<ProviderGuideStep>,
)

@Composable
internal fun ProviderUsageGuide(
    provider: OnlineRecognitionProvider,
    isMiuix: Boolean,
    performHaptic: () -> Unit,
    miuixHorizontalPadding: Dp = 12.dp,
) {
    val sections = remember(provider) { providerGuideSections(provider) }
    if (sections.isEmpty()) return

    var expanded by remember(provider) { mutableStateOf(false) }
    val toggleExpanded = {
        performHaptic()
        expanded = !expanded
    }

    if (isMiuix) {
        MiuixCard(
            onClick = toggleExpanded,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = miuixHorizontalPadding)
                .padding(bottom = 12.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MiuixText(
                        text = "${provider.displayName}使用说明",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    MiuixIcon(
                        imageVector = if (expanded) {
                            MiuixIcons.Regular.ExpandLess
                        } else {
                            MiuixIcons.Regular.ExpandMore
                        },
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    MiuixProviderGuideSections(sections, performHaptic)
                }
            }
        }
    } else {
        Surface(
            onClick = toggleExpanded,
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${provider.displayName}使用说明",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.Md3eExpandLess
                        } else {
                            Icons.Default.Md3eExpandMore
                        },
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Md3eProviderGuideSections(sections, performHaptic)
                }
            }
        }
    }
}

@Composable
private fun MiuixProviderGuideSections(
    sections: List<ProviderGuideSection>,
    performHaptic: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                section.title?.let {
                    MiuixText(
                        text = it,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
                section.steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        MiuixText(
                            text = "${index + 1}.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MiuixText(
                                text = step.text,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            if (step.link != null && step.linkLabel != null) {
                                MiuixText(
                                    text = step.linkLabel,
                                    modifier = Modifier.clickable {
                                        performHaptic()
                                        uriHandler.openUri(step.link)
                                    },
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MiuixTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Md3eProviderGuideSections(
    sections: List<ProviderGuideSection>,
    performHaptic: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                section.title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                section.steps.forEachIndexed { index, step ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = "${index + 1}.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = step.text,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (step.link != null && step.linkLabel != null) {
                                Text(
                                    text = step.linkLabel,
                                    modifier = Modifier.clickable {
                                        performHaptic()
                                        uriHandler.openUri(step.link)
                                    },
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun providerGuideSections(
    provider: OnlineRecognitionProvider,
): List<ProviderGuideSection> = when (provider) {
    OnlineRecognitionProvider.ZHIPU -> listOf(
        ProviderGuideSection(
            steps = listOf(
                ProviderGuideStep(
                    text = "打开智谱开放平台官网进行注册，并按照指示完成实名认证。",
                    linkLabel = "https://bigmodel.cn/",
                    link = "https://bigmodel.cn/",
                ),
                ProviderGuideStep(
                    text = "实名认证后，进入 API 管理页面。",
                    linkLabel = "https://bigmodel.cn/apikey/platform",
                    link = "https://bigmodel.cn/apikey/platform",
                ),
                ProviderGuideStep(
                    text = "点击右上角的“新建 key”按钮，填写备注名，将生成的密钥复制到上方输入框并选择模型即可使用。",
                ),
                ProviderGuideStep(
                    text = "推荐模型：GLM-4.6V-FlashX（最便宜而且速度最快）",
                ),
            ),
        ),
    )

    OnlineRecognitionProvider.MIMO -> listOf(
        ProviderGuideSection(
            steps = listOf(
                ProviderGuideStep(
                    text = "打开小米 MiMo 开放平台官网，并确认使用方式为 Token Plan 或按量计费。",
                    linkLabel = "https://platform.xiaomimimo.com/",
                    link = "https://platform.xiaomimimo.com/",
                ),
            ),
        ),
        ProviderGuideSection(
            title = "按量计费（钱包余额）",
            steps = listOf(
                ProviderGuideStep(
                    text = "注册并按照指示完成实名认证。实名认证成功后有 5 元按量计费赠金供测试。",
                ),
                ProviderGuideStep(
                    text = "实名认证后，进入 API Key 管理页面。",
                    linkLabel = "https://platform.xiaomimimo.com/console/api-keys",
                    link = "https://platform.xiaomimimo.com/console/api-keys",
                ),
                ProviderGuideStep(
                    text = "点击右上角的“新建 API key”按钮并填写备注名，将生成的密钥复制到上方输入框，计费方式选择“按量计费”，然后选择模型即可使用。",
                ),
            ),
        ),
        ProviderGuideSection(
            title = "Token Plan（套餐）",
            steps = listOf(
                ProviderGuideStep(
                    text = "登录后进入 Token Plan 管理页面。",
                    linkLabel = "https://platform.xiaomimimo.com/console/plan-manage",
                    link = "https://platform.xiaomimimo.com/console/plan-manage",
                ),
                ProviderGuideStep(
                    text = "在下方的专属 API key 处点击复制，将密钥粘贴到上方输入框，计费方式选择“Token Plan”，然后选择模型即可使用。",
                ),
            ),
        ),
    )

    OnlineRecognitionProvider.MINIMAX -> listOf(
        ProviderGuideSection(
            steps = listOf(
                ProviderGuideStep(
                    text = "打开 MiniMax 开放平台官网进行注册或登录，并按照指引完成实名认证。实名认证成功后赠送 15 元代金券。完成后请确认使用方式为按量计费或 Token Plan。",
                    linkLabel = "https://platform.minimaxi.com/console/",
                    link = "https://platform.minimaxi.com/console/",
                ),
            ),
        ),
        ProviderGuideSection(
            title = "按量计费",
            steps = listOf(
                ProviderGuideStep(
                    text = "进入账户管理页面，按照平台指引开通按量计费并获取 API Key。",
                    linkLabel = "https://platform.minimaxi.com/console/access",
                    link = "https://platform.minimaxi.com/console/access",
                ),
                ProviderGuideStep(
                    text = "将获取的 API Key 复制到上方输入框，然后选择模型即可使用。",
                ),
            ),
        ),
        ProviderGuideSection(
            title = "Token Plan（套餐）",
            steps = listOf(
                ProviderGuideStep(
                    text = "进入 Token Plan 页面，根据需要开通套餐并获取对应的 API Key。",
                    linkLabel = "https://platform.minimaxi.com/console/plan",
                    link = "https://platform.minimaxi.com/console/plan",
                ),
                ProviderGuideStep(
                    text = "将套餐对应的 API Key 复制到上方输入框，然后选择模型即可使用。",
                ),
            ),
        ),
    )

    OnlineRecognitionProvider.MOONSHOT -> listOf(
        ProviderGuideSection(
            steps = listOf(
                ProviderGuideStep(
                    text = "打开Kimi API 开放平台官网进行注册或登录，并按照指引完成实名认证。实名认证成功后赠送 15 元代金券。",
                    linkLabel = "https://platform.kimi.com/console/",
                    link = "https://platform.kimi.com/console/",
                ),
                ProviderGuideStep(
                    text = "进入 API Key 管理页面，创建并复制 API Key。",
                    linkLabel = "https://platform.kimi.com/console/api-keys",
                    link = "https://platform.kimi.com/console/api-keys",
                ),
                ProviderGuideStep(
                    text = "将获取的 API Key 复制到上方输入框，然后选择模型即可使用。",
                ),
            ),
        ),
    )

    OnlineRecognitionProvider.OPENCODE_ZEN -> listOf(
        ProviderGuideSection(
            steps = listOf(
                ProviderGuideStep(
                    text = "进入 OpenCode 账号页面，点击列表中的“API 密钥”，创建并复制你的 API Key（免费模型也需要 API Key）。",
                    linkLabel = "https://opencode.ai/auth",
                    link = "https://opencode.ai/auth",
                ),
                ProviderGuideStep(
                    text = "OpenCode Zen 免费额度由 opencode teams 运营的大规模模型 provider 提供，每天仅有部分限量额度给免费用户使用，所有用量信息均以 opencode teams 的调整为准。",
                ),
                ProviderGuideStep(
                    text = "将 API Key 复制到上方输入框，然后选择模型“MiMo-V2.5 Free”即可使用。",
                ),
            ),
        ),
    )

    OnlineRecognitionProvider.OPENCODE_GO -> listOf(
        ProviderGuideSection(
            steps = listOf(
                ProviderGuideStep(
                    text = "通过专属链接注册并订阅 OpenCode Go（约 $10/月）。使用此链接可额外获得 5 美金使用额度（不可叠加，仅抵消回血用）。",
                    linkLabel = "https://opencode.ai/go?ref=TGNQAJ1FDP",
                    link = "https://opencode.ai/go?ref=TGNQAJ1FDP",
                ),
                ProviderGuideStep(
                    text = "进入 OpenCode 账号页面，点击列表中的“API 密钥”，创建并复制你的 API Key。",
                    linkLabel = "https://opencode.ai/auth",
                    link = "https://opencode.ai/auth",
                ),
                ProviderGuideStep(
                    text = "由于部分模型不允许在中国提供服务，我们剔除了相关模型。",
                ),
                ProviderGuideStep(
                    text = "将 API Key 复制到上方输入框，然后选择模型即可使用。",
                ),
                ProviderGuideStep(
                    text = "Go 订阅有 5 小时、每周和每月用量限制，超过限制后可回退到 Zen 余额继续使用（需在控制台开启）。",
                ),
            ),
        ),
    )

    OnlineRecognitionProvider.DEEPSEEK -> listOf(
        ProviderGuideSection(
            steps = listOf(
                ProviderGuideStep(
                    text = "打开 DeepSeek 开放平台官网进行注册或登录。",
                    linkLabel = "https://platform.deepseek.com/",
                    link = "https://platform.deepseek.com/",
                ),
                ProviderGuideStep(
                    text = "进入 API Keys 页面，创建并复制你的 API Key。",
                    linkLabel = "https://platform.deepseek.com/api_keys",
                    link = "https://platform.deepseek.com/api_keys",
                ),
                ProviderGuideStep(
                    text = "将 API Key 复制到上方输入框，然后选择模型“DeepSeek V4 Flash Vision Exp”即可使用。",
                ),
            ),
        ),
    )

    else -> emptyList()
}

@Composable
private fun Md3eSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun SettingsCard(
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            content = content,
        )
    }
}

@Composable
private fun Md3eProviderDropdown(
    provider: OnlineRecognitionProvider,
    onSelected: (OnlineRecognitionProvider) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        Md3eDropdownAnchor(
            title = "供应商",
            selectedText = provider.displayName,
            expanded = expanded,
            selectedIcon = { OnlineRecognitionProviderIcon(provider, Modifier.size(28.dp)) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OnlineRecognitionProvider.entries.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.displayName) },
                    leadingIcon = { OnlineRecognitionProviderIcon(item, Modifier.size(28.dp)) },
                    onClick = {
                        onSelected(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Md3eModelDropdown(
    model: OnlineRecognitionModel,
    models: List<OnlineRecognitionModel>,
    summary: String = model.displayName,
    onSelected: (OnlineRecognitionModel) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (models.isNotEmpty()) expanded = !expanded },
    ) {
        Md3eDropdownAnchor(
            title = "模型",
            selectedText = summary,
            expanded = expanded,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.displayName) },
                    onClick = {
                        onSelected(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun customModelSummary(
    model: OnlineRecognitionModel,
    models: List<OnlineRecognitionModel>,
    loading: Boolean,
    error: String?,
): String = when {
    loading -> "正在获取模型…"
    error != null -> error
    models.isEmpty() -> "填写请求地址和 API 密钥后获取"
    model.id.isNotBlank() -> model.displayName
    else -> "请选择模型"
}

@Composable
private fun Md3eDropdown(
    title: String,
    selectedText: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        Md3eDropdownAnchor(
            title = title,
            selectedText = selectedText,
            expanded = expanded,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(key)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun Md3eDropdownAnchor(
    title: String,
    selectedText: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    selectedIcon: (@Composable () -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedIcon != null) {
                    selectedIcon()
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = selectedText,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
    }
}

private object ApiKeyPasteMenuKey

private fun Modifier.apiKeyPasteContextMenu(
    canPaste: () -> Boolean,
    onPaste: () -> Unit,
): Modifier = filterTextContextMenuComponents { component ->
    component.key != TextContextMenuKeys.PasteKey || !canPaste()
}.appendTextContextMenuComponents {
    if (canPaste()) {
        item(
            key = ApiKeyPasteMenuKey,
            label = "粘贴",
        ) {
            onPaste()
            close()
        }
    }
}

private const val CUSTOM_PROVIDER_WARNING =
    "使用第三方供应商时，我们无法保证您的第三方供应商是否安全，请自行辨别。"

private const val CUSTOM_MODEL_RECOMMENDATION =
    "您应该使用多模态模型作为识别模型。为了保证使用体验，请选择参数量相对较小、" +
        "非思考模式下也足够聪明且速度较快的模型，如 GLM-V4.7-Flash、MiMo 2.5 等。"
