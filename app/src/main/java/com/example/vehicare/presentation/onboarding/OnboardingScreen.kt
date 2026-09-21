package com.example.vehicare.presentation.onboarding

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vehicare.R
import com.example.vehicare.domain.repository.PreferencesRepository
import com.example.vehicare.ui.components.PrimaryButton
import com.example.vehicare.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class OnboardingPage(val icon: ImageVector, val title: String, val description: String)

private val pages = listOf(
    OnboardingPage(
        Icons.Default.DirectionsCar,
        "Know Your Vehicle Better",
        "Keep your vehicle information organized and access personalized health assessments in one place."
    ),
    OnboardingPage(
        Icons.Default.Quiz,
        "Describe What You Notice",
        "Answer guided questions about unusual sounds, warning lights, performance changes, and other vehicle symptoms."
    ),
    OnboardingPage(
        Icons.Default.Psychology,
        "Explore Probable Issues",
        "Use Bayesian-powered assessments to understand possible causes and prioritize the issues that may need attention."
    )
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    /** Marks onboarding as seen. Onboarding is optional and shown at most once (Section 1.2). */
    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(true)
            onDone()
        }
    }
}

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Dimens.ScreenPadding)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { viewModel.complete(onFinished) }) {
                Text("Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (index == 0) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_vehicare_logo),
                        contentDescription = "VehiCare AI logo",
                        modifier = Modifier.size(140.dp)
                    )
                    Spacer(Modifier.height(Dimens.SpaceXl))
                } else {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .clip(RoundedCornerShape(Dimens.CornerXl))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = page.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Spacer(Modifier.height(Dimens.SpaceXxl))
                }

                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Dimens.SpaceMd))
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(Dimens.SpaceXs)
                        .height(Dimens.SpaceSm)
                        .width(if (selected) Dimens.SpaceXl else Dimens.SpaceSm)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                )
            }
        }

        Spacer(Modifier.height(Dimens.SpaceXl))
        PrimaryButton(
            text = if (pagerState.currentPage == pages.lastIndex) "Get Started" else "Next",
            onClick = {
                if (pagerState.currentPage == pages.lastIndex) {
                    viewModel.complete(onFinished)
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            }
        )
        Spacer(Modifier.height(Dimens.SpaceLg))
        TextButton(
            onClick = { viewModel.complete(onFinished) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Go to Home", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
