package com.sultonovmuzafar.smartiptv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sultonovmuzafar.smartiptv.R
import com.sultonovmuzafar.smartiptv.data.Source
import java.text.DateFormat
import java.util.Date

@Composable fun SourceScreen(state: LibraryState,model: LibraryViewModel,onAdd: ()->Unit) {
    var deleting by remember { mutableStateOf<Source?>(null) }
    var editingEpg by remember { mutableStateOf<Source?>(null) }
    var epgUrl by remember { mutableStateOf("") }
    Column {
        Row(Modifier.fillMaxWidth().padding(bottom=18.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(stringResource(R.string.playlists),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
            FilledTonalButton(enabled=!state.busy,onClick=onAdd,modifier=Modifier.focusRing()) { Icon(Icons.Rounded.Add,null); Text(stringResource(R.string.add)) }
        }
        LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            items(state.sources,key={it.id}) { source ->
                Card { Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Text(source.name,style=MaterialTheme.typography.titleLarge)
                    Text("${source.type.uppercase()} · ${itemCount(source.count)}",color=Mint,style=MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.updated_at,DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(source.updated))),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        TextButton(enabled=!state.busy,modifier=Modifier.focusRing(),onClick={model.refresh(source)}) { Icon(Icons.Rounded.Refresh,null,Modifier.size(18.dp)); Text(stringResource(R.string.refresh)) }
                        if(source.type!="stream" && source.type!="media") TextButton(enabled=!state.busy,modifier=Modifier.focusRing(),onClick={
                            if(source.type=="xtream" || source.epgUrl.isNotBlank()) model.refreshEpg(source) else { editingEpg=source; epgUrl="" }
                        }) { Text(stringResource(R.string.epg)) }
                        Spacer(Modifier.weight(1f))
                        IconButton(enabled=!state.busy,modifier=Modifier.focusRing(),onClick={deleting=source}) { Icon(Icons.Rounded.DeleteOutline,stringResource(R.string.delete)) }
                    }
                    if(source.type!="stream" && source.type!="media") TextButton(enabled=!state.busy,modifier=Modifier.focusRing(),onClick={editingEpg=source;epgUrl=source.epgUrl}) { Text(stringResource(R.string.set_epg)) }
                } }
            }
            if(state.sources.isEmpty()) item { EmptyState(Icons.AutoMirrored.Rounded.PlaylistAdd,stringResource(R.string.empty_title),stringResource(R.string.empty_body)) }
        }
    }
    deleting?.let { source -> AlertDialog(onDismissRequest={deleting=null},title={Text(stringResource(R.string.delete_playlist))},text={Text(stringResource(R.string.delete_confirm,source.name))},confirmButton={TextButton(modifier=Modifier.focusRing(),onClick={model.remove(source);deleting=null}) { Text(stringResource(R.string.delete)) }},dismissButton={TextButton(modifier=Modifier.focusRing(),onClick={deleting=null}) { Text(stringResource(R.string.cancel)) }}) }
    editingEpg?.let { source -> AlertDialog(onDismissRequest={editingEpg=null},title={Text(stringResource(R.string.epg))},text={OutlinedTextField(epgUrl,{epgUrl=it},label={Text(stringResource(R.string.xmltv_url))},singleLine=true)},confirmButton={TextButton(enabled=epgUrl.isNotBlank(),modifier=Modifier.focusRing(),onClick={model.updateEpg(source,epgUrl);editingEpg=null}) { Text(stringResource(R.string.save)) }},dismissButton={TextButton(modifier=Modifier.focusRing(),onClick={editingEpg=null}) { Text(stringResource(R.string.cancel)) }}) }
}
