package com.sultonovmuzafar.smartiptv.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sultonovmuzafar.smartiptv.R
import com.sultonovmuzafar.smartiptv.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class SourceOption(val id: String,val title: Int,val body: Int,val icon: ImageVector)
private val sourceOptions=listOf(
    SourceOption("url",R.string.playlist_url,R.string.source_url_help,Icons.Rounded.Link),
    SourceOption("xtream",R.string.xtream_codes,R.string.source_xtream_help,Icons.Rounded.Key),
    SourceOption("file",R.string.playlist_file,R.string.source_file_help,Icons.Rounded.Description),
    SourceOption("stream",R.string.single_stream,R.string.source_stream_help,Icons.Rounded.PlayCircle),
    SourceOption("media",R.string.local_media,R.string.source_media_help,Icons.Rounded.FolderOpen)
)

@Composable fun AddSourceDialog(model: LibraryViewModel,onClose: ()->Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val focus=LocalFocusManager.current
    val scope=rememberCoroutineScope()
    val tv=isTelevision()
    var type by rememberSaveable { mutableStateOf("url") }
    var details by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    // Provider addresses and credentials never enter the saved-instance-state bundle.
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var epg by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val defaultName=stringResource(R.string.my_playlist)
    val preview=state.sourcePreview
    val step=if(preview!=null) 3 else if(details) 2 else 1
    val choiceFocus=remember { FocusRequester() }
    val confirmFocus=remember { FocusRequester() }
    fun close() { if(!state.sourceSaving) { model.discardSource();onClose() } }
    fun back() {
        if(state.sourceSaving) return
        if(preview!=null) model.discardSource()
        else if(details) { model.discardSource();details=false }
        else close()
    }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            val mime=withContext(Dispatchers.IO) { runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty() }
            model.prepareSource(SourceRequest(name.ifBlank { defaultName },type,epgUrl=epg,documentUri=uri.toString(),mimeType=mime))
        }
    }
    fun prepare() {
        focus.clearFocus()
        if(type=="file") picker.launch(arrayOf("audio/*","application/*","text/*"))
        else if(type=="media") picker.launch(arrayOf("video/*","audio/*","image/*"))
        else model.prepareSource(SourceRequest(name.ifBlank { defaultName },type,url,user,password,epg))
    }
    val canPrepare=(type=="file" || type=="media" || url.isNotBlank()) && (type!="xtream" || user.isNotBlank() && password.isNotBlank())
    Dialog(onDismissRequest={close()},properties=DialogProperties(usePlatformDefaultWidth=false,dismissOnBackPress=false,dismissOnClickOutside=!state.sourceSaving,decorFitsSystemWindows=false)) {
        BackHandler(enabled=!state.sourceSaving) { back() }
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding(),contentAlignment=Alignment.Center) {
            val wide=maxWidth>700.dp && maxHeight>450.dp
            Surface(modifier=if(wide) Modifier.widthIn(max=780.dp).fillMaxWidth(.85f).fillMaxHeight(.92f) else Modifier.fillMaxSize(),shape=RoundedCornerShape(if(wide) 28.dp else 0.dp),color=Canvas) {
                Column(Modifier.fillMaxSize().padding(if(tv) 28.dp else 20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={back()},enabled=!state.sourceSaving,modifier=Modifier.focusRing()) { Icon(if(step==1) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,stringResource(if(step==1) R.string.close else R.string.back)) }
                        Column(Modifier.weight(1f).padding(start=8.dp)) {
                            Text(stringResource(R.string.add_source),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                            Text(stringResource(R.string.step_of,step,3),style=MaterialTheme.typography.labelMedium,color=Mint)
                        }
                    }
                    if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                        when {
                            preview!=null->SourcePreviewContent(preview)
                            details->SourceDetails(type,name,{name=it},url,{url=it},user,{user=it},password,{password=it},epg,{epg=it},showPassword,{showPassword=!showPassword},state.busy)
                            else->{
                                Text(stringResource(R.string.source_choose_title),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                                Text(stringResource(R.string.source_guide),color=MaterialTheme.colorScheme.onSurfaceVariant)
                                sourceOptions.forEachIndexed { index,option ->
                                    Card(onClick={type=option.id;details=true;model.discardSource()},modifier=Modifier.fillMaxWidth().then(if(index==0) Modifier.initialFocus(choiceFocus,tv,step) else Modifier).focusRing(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Panel)) {
                                        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                                            Icon(option.icon,null,tint=Mint,modifier=Modifier.size(28.dp))
                                            Column(Modifier.weight(1f).padding(horizontal=14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                                Text(stringResource(option.title),fontWeight=FontWeight.SemiBold)
                                                Text(stringResource(option.body),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Icon(Icons.AutoMirrored.Rounded.ArrowForward,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Text(stringResource(R.string.content_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        state.error?.let { Surface(color=MaterialTheme.colorScheme.errorContainer,shape=RoundedCornerShape(14.dp)) {
                            Text(stringResource(errorResource(it)),Modifier.fillMaxWidth().padding(16.dp),color=MaterialTheme.colorScheme.onErrorContainer)
                        } }
                    }
                    if(step>1) {
                        if(state.sourcePreparing) {
                            Text(stringResource(R.string.checking_source),style=MaterialTheme.typography.bodyMedium,color=Mint)
                            OutlinedButton(onClick={model.discardSource()},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).focusRing()) { Text(stringResource(R.string.cancel)) }
                        } else Button(
                            onClick={if(preview!=null) model.confirmSource() else prepare()},
                            enabled=!state.busy && (preview!=null || canPrepare),
                            modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).initialFocus(confirmFocus,tv && preview!=null && !state.busy,step).focusRing(),shape=RoundedCornerShape(16.dp)
                        ) { Text(stringResource(if(state.sourceSaving) R.string.importing else if(preview!=null) R.string.confirm_source else if(type=="file" || type=="media") R.string.choose_file else if(type=="stream") R.string.preview_source else R.string.check_source)) }
                    }
                }
            }
        }
    }
}

@Composable private fun SourceDetails(type: String,name: String,onName: (String)->Unit,url: String,onUrl: (String)->Unit,user: String,onUser: (String)->Unit,password: String,onPassword: (String)->Unit,epg: String,onEpg: (String)->Unit,showPassword: Boolean,onShowPassword: ()->Unit,busy: Boolean) {
    val option=sourceOptions.first { it.id==type }
    Text(stringResource(option.title),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
    Text(stringResource(option.body),color=MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(name,onName,modifier=Modifier.fillMaxWidth().focusRing(),label={Text(stringResource(R.string.source_name))},placeholder={Text(stringResource(R.string.my_playlist))},singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next))
    if(type!="file" && type!="media") OutlinedTextField(url,onUrl,modifier=Modifier.fillMaxWidth().focusRing(),label={Text(stringResource(if(type=="xtream") R.string.server_url else R.string.url))},placeholder={Text("https://…")},singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,imeAction=ImeAction.Next))
    if(type=="xtream") {
        OutlinedTextField(user,onUser,modifier=Modifier.fillMaxWidth().focusRing(),label={Text(stringResource(R.string.username))},singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Next))
        OutlinedTextField(password,onPassword,modifier=Modifier.fillMaxWidth().focusRing(),label={Text(stringResource(R.string.password))},singleLine=true,enabled=!busy,visualTransformation=if(showPassword) VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,imeAction=ImeAction.Done),trailingIcon={IconButton(onClick=onShowPassword,modifier=Modifier.focusRing()) { Icon(if(showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,stringResource(R.string.show_password)) }})
    }
    if(type=="url" || type=="file") {
        Text(stringResource(R.string.epg_explanation),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(epg,onEpg,modifier=Modifier.fillMaxWidth().focusRing(),label={Text(stringResource(R.string.epg_optional))},singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri,imeAction=ImeAction.Done))
    }
    if(type=="file" || type=="media") Surface(color=Panel,shape=RoundedCornerShape(16.dp)) { Text(stringResource(R.string.document_import_help),Modifier.padding(16.dp),style=MaterialTheme.typography.bodyMedium) }
}

@Composable private fun SourcePreviewContent(preview: SourcePreview) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.CheckCircle,null,tint=Mint,modifier=Modifier.size(36.dp))
        Column { Text(stringResource(R.string.source_ready),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);Text(preview.name,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Text(stringResource(if(preview.type=="stream" || preview.type=="media") R.string.source_preview_unverified else R.string.source_preview_checked),color=MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(color=Panel,shape=RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(itemCount(preview.total),style=MaterialTheme.typography.headlineMedium,color=Mint,fontWeight=FontWeight.Bold)
            preview.counts.forEach { (kind,count) -> Row { Text(stringResource(kindLabel(kind)),Modifier.weight(1f));Text(count.toString(),fontWeight=FontWeight.SemiBold) } }
            if(preview.groupCount>0) Text(pluralStringResource(R.plurals.category_count,preview.groupCount,preview.groupCount),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(preview.hasGuide) Text(stringResource(R.string.guide_detected),style=MaterialTheme.typography.bodySmall,color=Mint)
        }
    }
    Text(stringResource(R.string.preview_titles),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
    preview.samples.forEach { item -> Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
        Icon(if(item.kind=="LIVE") Icons.Rounded.LiveTv else Icons.Rounded.PlayCircle,null,tint=Mint,modifier=Modifier.size(24.dp))
        Column(Modifier.padding(start=12.dp)) { Text(item.name);if(item.group.isNotBlank()) Text(item.group,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    } }
    Text(stringResource(R.string.preview_confirm_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
