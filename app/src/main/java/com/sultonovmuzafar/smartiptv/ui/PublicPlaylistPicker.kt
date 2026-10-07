package com.sultonovmuzafar.smartiptv.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.sultonovmuzafar.smartiptv.R

data class PublicPlaylistSample(val id: String,@StringRes val title: Int,val url: String)

@Composable internal fun PublicPlaylistPicker(samples: List<PublicPlaylistSample>,focus: FocusRequester,onChoose: (PublicPlaylistSample,String)->Unit) {
    Text(stringResource(R.string.public_playlists_title),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
    Text(stringResource(R.string.public_playlists_body),color=MaterialTheme.colorScheme.onSurfaceVariant)
    samples.forEachIndexed { index,sample ->
        val title=stringResource(sample.title)
        val name=stringResource(R.string.public_playlist_name,title)
        Card(onClick={onChoose(sample,name)},modifier=Modifier.fillMaxWidth().then(if(index==0) Modifier.initialFocus(focus,isTelevision()) else Modifier).focusRing(),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Panel)) {
            Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Rounded.Public,null,tint=Mint)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(title,fontWeight=FontWeight.SemiBold)
                    Text(sample.url,style=MaterialTheme.typography.bodySmall.copy(textDirection=TextDirection.Ltr),color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Rounded.ArrowForward,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    PublicPlaylistNotice()
}

@Composable internal fun PublicPlaylistNotice() {
    Surface(color=Panel,shape=RoundedCornerShape(14.dp)) {
        Text(stringResource(R.string.public_playlists_notice),Modifier.fillMaxWidth().padding(16.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
