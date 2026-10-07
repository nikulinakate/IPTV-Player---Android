package com.sultonovmuzafar.smartiptv.ui

import androidx.compose.foundation.Canvas as DrawingCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sultonovmuzafar.smartiptv.R

/** Native artwork adapts to TV/phone sizes and uses localized labels. */
@Composable fun OnboardingArtwork(page: Int,modifier: Modifier=Modifier) {
    Box(modifier.clip(RoundedCornerShape(32.dp)).background(Brush.linearGradient(listOf(Color(0xFF203F43),Panel,Color(0xFF202D4A)))),contentAlignment=Alignment.Center) {
        when(page) {
            0->Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("M3U","Xtream","M3U Plus").forEach { label ->
                        Surface(color=Canvas.copy(alpha=.7f),shape=RoundedCornerShape(12.dp)) { Text(label,Modifier.padding(12.dp,10.dp),style=MaterialTheme.typography.labelLarge,color=Mint) }
                    }
                }
                Icon(Icons.Rounded.South,null,tint=Mint)
                Surface(shape=RoundedCornerShape(20.dp),color=Canvas,modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically) {
                        BrandMark(48)
                        Column(Modifier.weight(1f).padding(horizontal=14.dp)) {
                            Text(stringResource(R.string.art_your_library),fontWeight=FontWeight.Bold)
                            Text(stringResource(R.string.art_ready_to_watch),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.CheckCircle,null,tint=Mint)
                    }
                }
            }
            1->Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.art_your_library),fontWeight=FontWeight.Bold,color=Color.White)
                listOf(R.string.live_tv to Icons.Rounded.LiveTv,R.string.movies to Icons.Rounded.Movie,R.string.series to Icons.Rounded.VideoLibrary).forEachIndexed { index,(label,icon) ->
                    Surface(color=if(index==0) Mint else Canvas.copy(alpha=.85f),shape=RoundedCornerShape(14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(icon,null,tint=if(index==0) Canvas else Mint)
                            Text(stringResource(label),Modifier.weight(1f).padding(start=12.dp),color=if(index==0) Canvas else Color.White)
                            Icon(if(index==0) Icons.Rounded.PlayArrow else Icons.Rounded.FavoriteBorder,null,tint=if(index==0) Canvas else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            else->Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Surface(shape=RoundedCornerShape(16.dp),color=Canvas,modifier=Modifier.width(60.dp).height(116.dp)) {
                        Box(Modifier.padding(8.dp),contentAlignment=Alignment.Center) { Icon(Icons.Rounded.PlayCircle,null,tint=Mint,modifier=Modifier.size(32.dp)) }
                    }
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward,null,tint=Mint)
                    Surface(shape=RoundedCornerShape(16.dp),color=Canvas,modifier=Modifier.weight(1f).height(148.dp)) {
                        Box(Modifier.padding(8.dp)) {
                            LandscapeScene(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)))
                            Icon(Icons.Rounded.PlayCircle,null,tint=Color.White,modifier=Modifier.align(Alignment.Center).size(42.dp))
                        }
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Wifi,null,tint=Mint)
                    Text(stringResource(R.string.art_same_wifi),style=MaterialTheme.typography.labelLarge,color=Color.White)
                }
            }
        }
    }
}

@Composable private fun LandscapeScene(modifier: Modifier) {
    DrawingCanvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF556CA5),Color(0xFFEFB78A))))
        drawCircle(Color(0xFFFFD8A1),radius=size.height*.16f,center=Offset(size.width*.74f,size.height*.26f))
        val far=Path().apply { moveTo(0f,size.height*.72f);lineTo(size.width*.28f,size.height*.34f);lineTo(size.width*.52f,size.height*.7f);lineTo(size.width*.73f,size.height*.5f);lineTo(size.width,size.height*.75f);lineTo(size.width,size.height);lineTo(0f,size.height);close() }
        drawPath(far,Color(0xFF416A76))
        val near=Path().apply { moveTo(0f,size.height*.88f);lineTo(size.width*.32f,size.height*.66f);lineTo(size.width*.55f,size.height*.85f);lineTo(size.width*.84f,size.height*.62f);lineTo(size.width,size.height*.83f);lineTo(size.width,size.height);lineTo(0f,size.height);close() }
        drawPath(near,Color(0xFF203F43))
    }
}
