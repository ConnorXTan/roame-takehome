package travel.roame.takehome.android

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.AndroidViewModel
import org.maplibre.android.MapLibre
import travel.roame.takehome.android.ui.AppRoot
import travel.roame.takehome.android.ui.FlightExplorerTheme
import travel.roame.takehome.createExplorerController

/** Survives rotation, so a configuration change keeps the query, results and selection. */
class ExplorerViewModel(application: Application) : AndroidViewModel(application) {
    val controller = createExplorerController(application, BuildConfig.API_BASE_URL)

    override fun onCleared() = controller.close()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContent {
            FlightExplorerTheme { AppRoot() }
        }
    }
}
