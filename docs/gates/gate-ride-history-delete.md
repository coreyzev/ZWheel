# Gate: Delete Ride History Entry

## Purpose

Users need to be able to delete individual rides from history. No delete
functionality exists anywhere in the current stack (DAO → repo → VM → UI).

---

## Implementation

### 1. `app/src/main/kotlin/com/zwheel/app/data/ride/RideDao.kt`

Add two queries after the existing `getAllSessions` / `getPointsForSession` queries:

```kotlin
@Query("DELETE FROM ride_point WHERE sessionId = :sessionId")
suspend fun deletePointsForSession(sessionId: String)

@Query("DELETE FROM ride_session WHERE id = :sessionId")
suspend fun deleteSession(sessionId: String)
```

### 2. `app/src/main/kotlin/com/zwheel/app/data/ride/RideRepository.kt`

Add a function that deletes points first, then the session:

```kotlin
suspend fun deleteSession(sessionId: String) {
    dao.deletePointsForSession(sessionId)
    dao.deleteSession(sessionId)
}
```

### 3. `app/src/main/kotlin/com/zwheel/app/ui/history/RideHistoryViewModel.kt`

Add a delete function:

```kotlin
fun deleteSession(sessionId: String) {
    viewModelScope.launch { repository.deleteSession(sessionId) }
}
```

### 4. `app/src/main/kotlin/com/zwheel/app/ui/history/RideHistoryScreen.kt`

**Add imports** (check which are already present):

```kotlin
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
```

**Update `RideHistoryScreen` signature** to pass delete through:

```kotlin
@Composable
fun RideHistoryScreen(
    viewModel: RideHistoryViewModel = hiltViewModel(),
    onRideClick: (sessionId: String) -> Unit = {},
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val isBoardConnected by viewModel.isBoardConnected.collectAsStateWithLifecycle()

    HistoryListContent(
        sessions = sessions,
        isBoardConnected = isBoardConnected,
        onRideClick = onRideClick,
        onDeleteSession = viewModel::deleteSession,
    )
}
```

**Update `HistoryListContent`** to accept and forward `onDeleteSession`:

Add `onDeleteSession: (String) -> Unit = {}` to its parameter list.

In the `items(sessions)` block, wrap each session card with `SwipeToDismissBox`.
Replace the existing item composable call (whatever renders the session row) with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
items(sessions, key = { it.sessionId }) { session ->
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart ||
                value == SwipeToDismissBoxValue.StartToEnd
            ) {
                onDeleteSession(session.sessionId)
                true
            } else {
                false
            }
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.4f },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val c = LocalZWheelColors.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp)
                    .background(
                        color = androidx.compose.ui.graphics.Color(0xFFB00020),
                        shape = RoundedCornerShape(12.dp),
                    ),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                    Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete ride",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        },
    ) {
        // original session card composable here — whatever was in the items block before
    }
}
```

The key point: put whatever composable was previously the items block body inside
the `SwipeToDismissBox` trailing lambda. Do not change the card's appearance.

---

## Compile check

```
gradle :app:compileDebugKotlin
```

Must pass with zero errors.

---

## Commit

```
feat(history): swipe-to-delete ride history entries
```

No Co-Authored-By line.
