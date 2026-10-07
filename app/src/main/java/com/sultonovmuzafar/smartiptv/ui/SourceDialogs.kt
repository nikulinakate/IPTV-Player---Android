package com.sultonovmuzafar.smartiptv.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sultonovmuzafar.smartiptv.R
import com.sultonovmuzafar.smartiptv.data.Source
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@Composable fun AddSourceDialog(busy: Boolean,model: LibraryViewModel,onClose: ()->Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var type by rememberSaveable { mutableStateOf("url") }
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var epg by rememberSaveable { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val defaultName=stringResource(R.string.my_playlist)
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) {
            try { context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) {}
            model.add(name.ifBlank { defaultName },type,"",epg=epg,uri=uri)
        }
    }
    AlertDialog(onDismissRequest={if(!busy) onClose()},icon={Icon(Icons.AutoMirrored.Rounded.PlaylistAdd,null,tint=Mint)},title={Text(stringResource(R.string.add_source))},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.source_guide),color=MaterialTheme.colorScheme.onSurfaceVariant)
            listOf("url" to R.string.playlist_url,"xtream" to R.string.xtream_codes,"file" to R.string.playlist_file,"stream" to R.string.single_stream,"media" to R.string.local_media).forEach { (key,label) ->
                FilterChip(selected=type==key,onClick={if(!busy) type=key},label={Text(stringResource(label))},modifier=Modifier.fillMaxWidth())
            }
            OutlinedTextField(name,{name=it},label={Text(stringResource(R.string.source_name))},singleLine=true,enabled=!busy)
            if(type!="file" && type!="media") OutlinedTextField(url,{url=it},label={Text(stringResource(if(type=="xtream") R.string.server_url else R.string.url))},placeholder={Text("https://…")},singleLine=true,enabled=!busy)
            if(type=="xtream") {
                OutlinedTextField(user,{user=it},label={Text(stringResource(R.string.username))},singleLine=true,enabled=!busy)
                OutlinedTextField(password,{password=it},label={Text(stringResource(R.string.password))},singleLine=true,enabled=!busy,visualTransformation=if(showPassword) VisualTransformation.None else PasswordVisualTransformation(),trailingIcon={IconButton(onClick={showPassword=!showPassword}) { Icon(if(showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,stringResource(R.string.show_password)) }})
            }
            if(type=="url" || type=="file") OutlinedTextField(epg,{epg=it},label={Text(stringResource(R.string.epg_optional))},singleLine=true,enabled=!busy)
            Text(stringResource(R.string.content_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            state.error?.let { Text(stringResource(errorResource(it)),color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={Button(enabled=!busy && (type=="file" || type=="media" || url.isNotBlank()) && (type!="xtream" || (user.isNotBlank() && password.isNotBlank())),onClick={
        if(type=="file") picker.launch(arrayOf("audio/*","application/*","text/*"))
        else if(type=="media") picker.launch(arrayOf("video/*","audio/*","image/*"))
        else model.add(name.ifBlank { defaultName },type,url,user,password,epg)
    }) { Text(stringResource(if(busy) R.string.importing else if(type=="file" || type=="media") R.string.choose_file else R.string.connect)) }},dismissButton={TextButton(enabled=!busy,onClick=onClose) { Text(stringResource(R.string.cancel)) }})
}

@Composable fun SourceScreen(state: LibraryState,model: LibraryViewModel,onAdd: ()->Unit) {
    var deleting by remember { mutableStateOf<Source?>(null) }
    var editingEpg by remember { mutableStateOf<Source?>(null) }
    var epgUrl by remember { mutableStateOf("") }
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom=18.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.playlists),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            FilledTonalButton(onClick=onAdd) { Icon(Icons.Rounded.Add,null); Text(stringResource(R.string.add)) }
        }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            items(state.sources,key={it.id}) { source ->
                Card { Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(source.name,style=MaterialTheme.typography.titleLarge)
                    Text("${source.type.uppercase()} · ${stringResource(R.string.items_count,source.count)}",color=Mint,style=MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.updated_at,DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(source.updated))),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        TextButton(enabled=!state.busy,onClick={model.refresh(source)}) { Icon(Icons.Rounded.Refresh,null,Modifier.size(18.dp)); Text(stringResource(R.string.refresh)) }
                        if(source.type!="stream" && source.type!="media") TextButton(enabled=!state.busy,onClick={
                            if(source.type=="xtream" || source.epgUrl.isNotBlank()) model.refreshEpg(source) else { editingEpg=source; epgUrl="" }
                        }) { Text(stringResource(R.string.epg)) }
                        Spacer(Modifier.weight(1f))
                        IconButton(enabled=!state.busy,onClick={deleting=source}) { Icon(Icons.Rounded.DeleteOutline,stringResource(R.string.delete)) }
                    }
                    if(source.type!="stream" && source.type!="media") TextButton(enabled=!state.busy,onClick={editingEpg=source;epgUrl=source.epgUrl}) { Text(stringResource(R.string.set_epg)) }
                } }
            }
            if(state.sources.isEmpty()) item { EmptyState(Icons.AutoMirrored.Rounded.PlaylistAdd,stringResource(R.string.empty_title),stringResource(R.string.empty_body)) }
        }
    }
    deleting?.let { source -> AlertDialog(onDismissRequest={deleting=null},title={Text(stringResource(R.string.delete_playlist))},text={Text(stringResource(R.string.delete_confirm,source.name))},confirmButton={TextButton(onClick={model.remove(source);deleting=null}) { Text(stringResource(R.string.delete)) }},dismissButton={TextButton(onClick={deleting=null}) { Text(stringResource(R.string.cancel)) }}) }
    editingEpg?.let { source -> AlertDialog(onDismissRequest={editingEpg=null},title={Text(stringResource(R.string.epg))},text={OutlinedTextField(epgUrl,{epgUrl=it},label={Text(stringResource(R.string.xmltv_url))},singleLine=true)},confirmButton={TextButton(enabled=epgUrl.isNotBlank(),onClick={model.updateEpg(source,epgUrl);editingEpg=null}) { Text(stringResource(R.string.save)) }},dismissButton={TextButton(onClick={editingEpg=null}) { Text(stringResource(R.string.cancel)) }}) }
}
