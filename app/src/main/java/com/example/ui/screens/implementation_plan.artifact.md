# Repair Plan: Photo Playback Control, Speed Controls & Cast Integration

This implementation plan addresses three key user-reported issues:
1. **Photo Playback Control**: Pausing during photo slideshow presentation should halt playlist advancement.
2. **Speed Controls**: Add playback speed control (0.5x, 1.0x, 1.25x, 1.5x, 2.0x) for video playback.
3. **Cast Button**: Enable the cast button in `MediaDetailScreen` to trigger the Google Cast route selector dialog.

---

## Proposed Changes

### 1. `MediaDetailScreen.kt`
- **Photo Playback Pause Fix**:
  - Update `isPhotoPlaying` definition to include `isPlaying`:
    ```kotlin
    val isPhotoPlaying = !isVideo && !showPlayerMenu && !showDeleteDialog && isPlaying
    ```
  - This ensures that when the user taps Pause during a photo slideshow, the advancement timer pauses immediately.
- **Video Speed Controls**:
  - Add a speed selection button/popup in the control row of `MediaDetailScreen.kt`.
  - Apply playback speed to `exoPlayer.setPlaybackSpeed(speed)` when selected.
- **Cast Route Dialog Integration**:
  - Wire the cast button `onClick` action to show the MediaRouter dialog (`MediaRouteChooserDialogFragment` or `MediaRouteDialogFactory`).

---

## Verification Plan

### Automated Tests
- Build verification via `app:assembleDebug`.

### Manual Verification
- Deploy to physical device (`ZL8323SNZJ`) and verify:
  1. Tapping Pause on a photo stops the slideshow from auto-advancing.
  2. Changing playback speed adjusts video speed correctly.
  3. Tapping the cast button opens the cast device selection dialog.
