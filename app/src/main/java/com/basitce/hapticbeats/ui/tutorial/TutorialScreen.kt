package com.basitce.hapticbeats.ui.tutorial

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TutorialScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Skip Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (pagerState.currentPage < 3) {
                TextButton(onClick = onFinish) {
                    Text(androidx.compose.ui.res.stringResource(com.basitce.hapticbeats.R.string.skip))
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            TutorialPage(page = page)
        }

        // Indicators and Next/Finish Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicators
            Row {
                repeat(4) { iteration ->
                    val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else Color.Gray
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .size(10.dp)
                    )
                }
            }

            // Button
            Button(
                onClick = {
                    if (pagerState.currentPage < 3) {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onFinish()
                    }
                }
            ) {
                Text(if (pagerState.currentPage < 3) androidx.compose.ui.res.stringResource(com.basitce.hapticbeats.R.string.next) else androidx.compose.ui.res.stringResource(com.basitce.hapticbeats.R.string.get_started))
            }
        }
    }
}

@Composable
fun TutorialPage(page: Int) {
    val (title, description, icon) = when (page) {
        0 -> Triple(
            androidx.compose.ui.res.stringResource(com.basitce.hapticbeats.R.string.tut_1_title),
            androidx.compose.ui.res.stringResource(com.basitce.hapticbeats.R.string.tut_1_desc),
            Icons.Default.PlayArrow // Placeholder
        )
        1 -> Triple(
            androidx.compose.ui.res.stringResource(com.basitce.hapticbeats.R.string.tut_2_title),
            androidx.compose.ui.res.stringResource(com.basitce.hapticbeats.R.string.tut_2_desc),
            Icons.Default.PlayArrow // Placeholder
        )
        2 -> Triple(
            androidx.compose.ui.res.stringResource(com.basitce.hapticbeats.R.string.tut_3_title),
            androidx.compose.ui.res.stringResource(com.basitce.hapticbeats.R.string.tut_3_desc),
            Icons.Default.PlayArrow // Placeholder
        )
        else -> Triple(
            androidx.compose.ui.res.stringResource(com.basitce.hapticbeats.R.string.tut_4_title),
            androidx.compose.ui.res.stringResource(com.basitce.hapticbeats.R.string.tut_4_desc),
            Icons.Default.Check
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
