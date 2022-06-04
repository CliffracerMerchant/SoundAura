![API](https://badgen.net/badge/API/24+/green)
# SoundAura

SoundAura is an open source ambient sound app. SoundAura does not include
any audio tracks to save on download size, but allows users to add files
from their local device and play any (reasonable) number of them concurrently,
with individual volume controls for each track.

SoundAura is intended to play audio in the background at the same time as the
user might be playing other audio (e.g. a podcast or audiobook). As such, it
behaves differently than most other audio apps in the following ways:
- It ignores normal audio focus rules and will keep playing
  even when other apps start playing audio. The exception is
  that it will pause during calls if the auto-pause during
  call option is enabled in the app settings.
- It uses a notification to show its controls instead of showing
  them in the media session section of the status bar.
  
The option to toggle behavior between this current behavior and an audio
focus respecting one is planned in the next release. 
  
SoundAura is built using:
- Kotlin
- MVVM paradigm (though without a repository layer due to there being only one data source)
- Room persistence library and Jetpack DataStore for the data
- Jetpack Compose for the UI
- Hilt for dependency injection
- Junit and Robolectric for testing

## Features
- A library of user-added tracks based on the device's local files.
- Individual track volume control
- A notification to control playback when the app is in the background.
- A quick settings tile to control playback. If the quick settings tile
  is in use, the notification can be manually hidden if desired to save
  notification space.
- Auto-pause during calls. Due to the fact that SoundAura ignores audio
  focus rules, this unfortunately requires the read phone state permission
  to function, and is enabled in the app settings.
- Auto-pause on audio-device changes: If SoundAura is playing, and an
  audio device change occurs that results in a system media volume of
  zero (e.g. the user unplugs or disconnects their headphones and the
  device's media volume is zero for its speaker), SoundAura will auto-pause
  its playback since it can't be heard anyways. If another audio device
  change occurs that makes the media volume go above zero, SoundAura
  will also automatically unpause itself unless the user manually
  affected the playback state in the mean time. No permissions required,
  except the read phone state permission for the optional auto-pause
  during calls.

## Planned Features
- The ability to switch between an audio-focus respecting mode and
  the current behavior.

## Screenshots

https://user-images.githubusercontent.com/42116365/171025181-05d4769b-f0aa-4a43-86c8-e8e9da5c2866.mp4

![ScreenShot1](media/screenshot1.png)
![ScreenShot2](media/screenshot2.png)
![ScreenShot3](media/screenshot3.png)
![ScreenShot4](media/screenshot4.png)

## Privacy Policy
SoundAura does not collect, store, or transmit any personal information.

## License
SoundAura's source code is released under the terms of the Apache License,
version 2.0. See the file 'license' in the repository's root directory to
see the full license text.