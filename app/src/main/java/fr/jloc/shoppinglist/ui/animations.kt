package fr.jloc.shoppinglist.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.unveilIn
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent

fun <T : Any> makeTransitionSpec(): (AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform) {
    return {
        ContentTransform(
            targetContentEnter = slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
            ),
            initialContentExit = slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
            ),
        )
    }
}

fun <T : Any> makePopTransitionSpec(): (AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform) {
    return {
        ContentTransform(
            targetContentEnter = slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
            ),
            initialContentExit = slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
            ),
        )
    }
}

fun <T : Any> makePredictivePopTransitionSpec(): (AnimatedContentTransitionScope<Scene<T>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform) {

    // The animation works in two phases:
    // - The exiting component is scaled down, revealing the entering component which is being
    //    scaled down as well, and is obscured by a scrim.
    // - The exiting component is faded and slided out, while the scrim on the entering component is
    //   faded out.

    val secondPhaseEasing = CubicBezierEasing(1.2f, 0f, .6f, -.1f)

    return {
        ContentTransform(
            unveilIn(tween(easing = secondPhaseEasing)) + scaleIn(initialScale = 1.1f),
            scaleOut(targetScale = 0.9f) + fadeOut(tween(easing = secondPhaseEasing)) + slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(easing = secondPhaseEasing),
            ),
        )
    }
}
