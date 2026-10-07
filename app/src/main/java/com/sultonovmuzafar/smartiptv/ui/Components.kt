package com.sultonovmuzafar.smartiptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.mediarouter.app.MediaRouteButton
import coil.compose.AsyncImage
import com.google.android.gms.cast.framework.CastButtonFactory
import com.sultonovmuzafar.smartiptv.R
import com.sultonovmuzafar.smartiptv.data.Channel
import com.sultonovmuzafar.smartiptv.playback.CastSupport

@Composable fun CastButton(modifier: Modifier=Modifier) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val available=remember { CastSupport.context(context)!=null }
    if(available) AndroidView(factory={ MediaRouteButton(it).apply { CastButtonFactory.setUpMediaRouteButton(it,this) } },modifier=modifier.size(48.dp))
}
@Composable fun BrandMark(size: Int=56) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size/3).dp)).background(Brush.linearGradient(listOf(Mint,MaterialTheme.colorScheme.secondary))),contentAlignment=Alignment.Center) {
        Icon(Icons.Rounded.LiveTv,null,Modifier.size((size*.52f).dp),tint=Canvas)
    }
}
@Composable fun EmptyState(icon: ImageVector,title: String,description: String,action: (@Composable ()->Unit)?=null) {
    Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Icon(icon,null,Modifier.size(54.dp),tint=Mint)
        Text(title,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
        Text(description,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
@Composable fun ChannelCard(channel: Channel,onPlay: ()->Unit,onFavorite: ()->Unit,onGuide: ()->Unit) {
    Card(onClick=onPlay,modifier=Modifier.fillMaxWidth().focusRing(RoundedCornerShape(20.dp)),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=Panel)) {
        Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
                if(channel.logo.isNotBlank()) AsyncImage(model=channel.logo,contentDescription=null,modifier=Modifier.size(42.dp))
                else Icon(if(channel.kind=="LIVE") Icons.Rounded.LiveTv else if(channel.kind=="SERIES") Icons.Rounded.VideoLibrary else Icons.Rounded.PlayArrow,null,tint=Mint)
            }
            Column(Modifier.weight(1f).padding(horizontal=12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(channel.name,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                Text(channel.group.ifEmpty { stringResource(kindLabel(channel.kind)) },style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(channel.position>0 && channel.kind !in listOf("LIVE","SERIES","IMAGE")) Text(stringResource(R.string.resume_at,resumeTime(channel.position)),style=MaterialTheme.typography.labelSmall,color=Mint)
            }
            if(channel.kind=="LIVE") IconButton(onClick=onGuide,modifier=Modifier.size(48.dp).focusRing()) { Icon(Icons.AutoMirrored.Rounded.EventNote,stringResource(R.string.guide),tint=MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick=onFavorite,modifier=Modifier.size(48.dp).focusRing()) { Icon(if(channel.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,stringResource(if(channel.favorite) R.string.remove_favorite else R.string.add_favorite),tint=if(channel.favorite) Mint else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}
@Composable fun ContinueWatching(channels: List<Channel>,onPlay: (Channel)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp),modifier=Modifier.padding(bottom=20.dp)) {
        Text(stringResource(R.string.continue_watching),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
        LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(2.dp)) {
            items(channels,key={it.id}) { channel ->
                Card(onClick={onPlay(channel)},modifier=Modifier.width(if(isTelevision()) 300.dp else 240.dp).focusRing(RoundedCornerShape(20.dp)),shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PlayCircle,null,tint=Mint,modifier=Modifier.size(36.dp))
                        Column(Modifier.weight(1f).padding(start=12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(channel.name,maxLines=2,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.SemiBold)
                            Text(stringResource(R.string.resume_at,resumeTime(channel.position)),style=MaterialTheme.typography.labelMedium,color=Mint)
                        }
                    }
                }
            }
        }
    }
}
fun kindLabel(kind: String)=when(kind) { "LIVE"->R.string.live_tv; "MOVIE"->R.string.movies; "SERIES"->R.string.series; else->R.string.media }

@Composable fun itemCount(count: Int)=pluralStringResource(R.plurals.item_count,count,count)
