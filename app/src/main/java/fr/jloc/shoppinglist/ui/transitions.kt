package fr.jloc.shoppinglist.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.unveilIn
import androidx.compose.ui.graphics.Color
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

@OptIn(ExperimentalAnimationApi::class)
fun <T : Any> makeTransitionSpec(): (AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform) =
    {
        ContentTransform(
            targetContentEnter = slideInHorizontally(
                initialOffsetX = { it / 10 },
            ) + unveilIn(
                initialColor = Color.White,
            ),
            initialContentExit = slideOutHorizontally(
                targetOffsetX = { -it / 10 },
            ) + fadeOut(),
        )
    }

@OptIn(ExperimentalAnimationApi::class)
fun <T : Any> makePopTransitionSpec(): (AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform) =
    {
        ContentTransform(
            targetContentEnter = slideInHorizontally(
                initialOffsetX = { -it / 10 },
            ) + unveilIn(
                initialColor = Color.White,
            ),
            initialContentExit = slideOutHorizontally(
                targetOffsetX = { it / 10 },
            ) + fadeOut(),
        )
    }

fun <T : Any> makePredictivePopTransitionSpec(): (AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform) =
    {
        makePopTransitionSpec<T>()()
    }
