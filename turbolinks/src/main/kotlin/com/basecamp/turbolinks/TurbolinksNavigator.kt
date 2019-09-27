package com.basecamp.turbolinks

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions

class TurbolinksNavigator(private val fragment: Fragment,
                          private val session: TurbolinksSession,
                          private val router: TurbolinksRouter) {

    var onNavigationVisit: (onNavigate: () -> Unit) -> Unit = { onReady ->
        onReady()
    }

    fun navigateUp(): Boolean {
        onNavigationVisit {}
        return currentController().navigateUp()
    }

    fun navigateBack() {
        popBackStack()
    }

    fun clearBackStack() {
        if (isAtStartDestination()) return

        onNavigationVisit {
            val controller = currentController()
            controller.popBackStack(controller.graph.startDestination, false)
        }
    }

    fun navigate(location: String, action: String, properties: PathProperties? = null): Boolean {
        val currentProperties = properties ?: currentPathConfiguration().properties(location)
        val currentContext = currentPresentationContext()
        val newContext = currentProperties.context
        val presentation = presentation(location, action)
        val navigateWithinContext = currentContext == newContext
        val restartModal = navigateWithinContext &&
                presentation == Presentation.REPLACE &&
                newContext == PresentationContext.MODAL

        logEvent("navigate", "location" to location,
            "action" to action, "currentContext" to currentContext,
            "newContext" to newContext, "presentation" to presentation)

        when {
            presentation == Presentation.NONE -> return false
            restartModal -> dismissModalContextWithResult(location, currentProperties)
            navigateWithinContext -> navigateWithinContext(location, currentProperties, presentation)
            newContext == PresentationContext.MODAL -> navigateToModalContext(location, currentProperties)
            newContext == PresentationContext.DEFAULT -> dismissModalContextWithResult(location, currentProperties)
        }

        return true
    }

    private fun navigateWithinContext(location: String, properties: PathProperties, presentation: Presentation) {
        logEvent("navigateWithinContext", "location" to location, "presentation" to presentation)
        val bundle = buildBundle(location, presentation)

        onNavigationVisit {
            if (presentation == Presentation.POP || presentation == Presentation.REPLACE) {
                currentController().popBackStack()
            }

            if (presentation == Presentation.REPLACE || presentation == Presentation.PUSH) {
                navigateToLocation(location, properties, bundle)
            }

            if (presentation == Presentation.REPLACE_ALL) {
                clearBackStack()
            }
        }
    }

    private fun navigateToModalContext(location: String, properties: PathProperties) {
        logEvent("navigateToModalContext", "location" to location)
        val bundle = buildBundle(location, Presentation.PUSH)

        onNavigationVisit {
            navigateToLocation(location, properties, bundle)
        }
    }

    private fun dismissModalContextWithResult(location: String, properties: PathProperties) {
        logEvent("dismissModalContextWithResult", "location" to location, "uri" to properties.uri)

        onNavigationVisit {
            val controller = currentController()
            val destination = controller.currentDestination

            if (destination == null) {
                logEvent("dismissModalContextWithResult", "error" to "No modal graph found")
                return@onNavigationVisit
            }

            sendModalResult(location, "advance")
            controller.popBackStack(destination.id, true)
        }
    }

    private fun sendModalResult(location: String, action: String) {
        if (fragment is TurbolinksWebFragment) {
            fragment.sharedViewModel.modalResult = TurbolinksModalResult(location, action)
        }
    }

    private fun presentation(location: String, action: String): Presentation {
        val locationIsRoot = locationsAreSame(location, session.rootLocation)
        val locationIsCurrent = locationsAreSame(location, currentLocation())
        val locationIsPrevious = locationsAreSame(location, previousLocation())
        val replace = action == "replace"

        return when {
            locationIsRoot && locationIsCurrent -> Presentation.NONE
            locationIsPrevious -> Presentation.POP
            locationIsRoot -> Presentation.REPLACE_ALL
            locationIsCurrent || replace -> Presentation.REPLACE
            else -> Presentation.PUSH
        }
    }

    private fun navigateToLocation(location: String, properties: PathProperties, bundle: Bundle) {
        logEvent("navigateToLocation", "location" to location, "uri" to properties.uri)

        if (router.shouldNavigate(location)) {
            val controller = currentController()
            val destination = controller.graph.find { it.hasDeepLink(properties.uri) }
            val options = router.getNavigationOptions(location, properties) ?: defaultNavOptions()

            if (destination == null) {
                logEvent("navigateToLocation", "uri" to properties.uri, "error" to "No deep link found")
                return
            }

            when (destination) {
                null -> logEvent("navigateToLocation", "uri" to properties.uri, "error" to "No destination found")
                else -> controller.navigate(destination.id, bundle, options)
            }
        }
    }

    private fun currentController(): NavController {
        return fragment.findNavController()
    }

    private fun popBackStack() {
        onNavigationVisit {
            if (!currentController().popBackStack()) {
                fragment.requireActivity().finish()
            }
        }
    }

    private fun isAtStartDestination(): Boolean {
        val controller = currentController()
        return controller.graph.startDestination == controller.currentDestination?.id
    }

    private fun locationsAreSame(first: String?, second: String?): Boolean {
        fun String.removeInconsequentialSuffix(): String {
            return this.removeSuffix("#").removeSuffix("/")
        }

        return first?.removeInconsequentialSuffix() == second?.removeInconsequentialSuffix()
    }

    private fun buildBundle(location: String, presentation: Presentation): Bundle {
        val previousLocation = when (presentation) {
            Presentation.PUSH -> currentLocation()
            else -> previousLocation()
        }

        return bundleOf(
            "location" to location,
            "previousLocation" to previousLocation,
            "sessionName" to session.sessionName
        )
    }

    private fun defaultNavOptions(): NavOptions {
        return navOptions {
            anim {
                enter = R.anim.nav_default_enter_anim
                exit = R.anim.nav_default_exit_anim
                popEnter = R.anim.nav_default_pop_enter_anim
                popExit = R.anim.nav_default_pop_exit_anim
            }
        }
    }

    private fun currentLocation(): String? {
        return fragment.arguments?.getString("location")
    }

    private fun previousLocation(): String? {
        return fragment.arguments?.getString("previousLocation")
    }

    private fun currentPathConfiguration(): PathConfiguration {
        return session.pathConfiguration
    }

    private fun currentPresentationContext(): PresentationContext {
        val location = currentLocation() ?: return PresentationContext.DEFAULT
        return currentPathConfiguration().properties(location).context
    }

    private fun logEvent(event: String, vararg params: Pair<String, Any>) {
        val attributes = params.toMutableList().apply {
            add(0, "session" to session.sessionName)
            add("fragment" to fragment.javaClass.simpleName)
        }
        logEvent(event, attributes)
    }
}
