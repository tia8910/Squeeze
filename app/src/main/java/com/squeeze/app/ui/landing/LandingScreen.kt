package com.squeeze.app.ui.landing

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.provider.Settings
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.squeeze.app.R
import com.squeeze.app.ui.brand.SqueezeMark
import com.squeeze.app.ui.components.PrimaryButton
import com.squeeze.app.ui.components.ThemeToggle
import com.squeeze.app.ui.components.SqueezeShape
import com.squeeze.app.ui.theme.SqueezeTheme
import com.squeeze.app.ui.theme.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Night = Color(0xFF050B18)
private val Sky = Color(0xFF8DB3FF)
private val SkyBright = Color(0xFF7DB4FF)
private val Mist = Color(0xFFD6E2F7)
private val Safe = Color(0xFF4ADE80)

/** Music volume when the user has turned sound on. Loud enough to feel, never startling. */
private const val MUSIC_VOLUME = 0.7f

/**
 * First-run welcome: the intro video full screen, the promise over it, one button.
 *
 * Always drawn dark, whatever the theme, because it sits on video. The video starts silent;
 * "Tap for sound" turns the music on and the choice is remembered. It also stays silent while
 * the phone is on silent or vibrate, until the user taps. People who turn animations off in
 * system settings get a still frame instead of video.
 */
@Composable
fun LandingScreen(
    onGetStarted: () -> Unit,
    onToggleTheme: () -> Unit = {},
    soundOn: Boolean = false,
    onSoundChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val reduceMotion = remember { animationsOff(context) }
    val ringerSilent = remember { ringerSilent(context) }
    // Silent or vibrate wins over the remembered choice, until the user taps on this visit.
    var tappedThisVisit by remember { mutableStateOf(false) }
    val audible = soundOn && (!ringerSilent || tappedThisVisit)

    val fade = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var leaving by remember { mutableStateOf(false) }
    val start: () -> Unit = {
        if (!leaving) {
            leaving = true
            scope.launch {
                // Music fades out with the screen instead of cutting off mid beat.
                if (audible) fade.animateTo(0f, tween(450))
                onGetStarted()
            }
        }
    }

    SqueezeTheme(themeMode = ThemeMode.DARK) {
        Box(Modifier.fillMaxSize().background(Night)) {
            if (reduceMotion) {
                Image(
                    painter = painterResource(R.drawable.intro_poster),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                IntroVideo(volume = if (audible) MUSIC_VOLUME * fade.value else 0f, modifier = Modifier.fillMaxSize())
            }

            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Night.copy(alpha = 0.55f),
                        0.22f to Color.Transparent,
                        0.42f to Color.Transparent,
                        0.66f to Night.copy(alpha = 0.82f),
                        1f to Night,
                    ),
                ),
            )

            LandingContent(
                audible = audible,
                showSound = !reduceMotion,
                onSound = {
                    tappedThisVisit = true
                    onSoundChange(!audible)
                },
                onToggleTheme = onToggleTheme,
                onGetStarted = start,
            )
        }
    }
}

@Composable
private fun LandingContent(
    audible: Boolean,
    showSound: Boolean,
    onSound: () -> Unit,
    onToggleTheme: () -> Unit,
    onGetStarted: () -> Unit,
) {
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }

    val words = listOf("Train for it.", "Eat for it.", "Track it.")
    var word by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            word = (word + 1) % words.size
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 26.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().alpha(reveal.value),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SqueezeMark(size = 36.dp, onDark = true)
            Text(
                "Squeeze Fit",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            if (showSound) SoundButton(audible = audible, onClick = onSound)
            ThemeToggle(onToggle = onToggleTheme)
        }

        Spacer(Modifier.weight(1f))

        Column(Modifier.alpha(reveal.value)) {
            Text(
                "YOUR REAL COACH IN YOUR POCKET",
                color = Sky,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
            )
            Text(
                "Know your body.",
                color = Color.White,
                fontSize = 34.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 8.dp),
            )
            AnimatedContent(
                targetState = word,
                transitionSpec = {
                    (slideInVertically { it / 2 } + fadeIn(tween(400))) togetherWith
                        (slideOutVertically { -it / 2 } + fadeOut(tween(300)))
                },
                label = "headline",
            ) { i ->
                Text(
                    words[i],
                    color = SkyBright,
                    fontSize = 34.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Text(
                "Body fat and every muscle group from one photo, then a plan built around it.",
                color = Mist,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(top = 10.dp),
            )
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(Icons.Filled.CenterFocusStrong, "Body scan")
                Pill(Icons.Filled.FitnessCenter, "Smart plan")
                Pill(Icons.Filled.Restaurant, "Fuel")
            }
            ShiningButton(Modifier.padding(top = 22.dp), onClick = onGetStarted)
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Safe, modifier = Modifier.size(14.dp))
                Text(
                    "Photos never leave your phone",
                    color = Color(0xFFC9D6F0),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SoundButton(audible: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .padding(end = 4.dp)
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(Night.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (audible) "Turn music off" else "Turn music on" }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (audible) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        if (!audible) {
            Text(
                "Tap for sound",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun Pill(icon: ImageVector, label: String) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Sky, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** The main button with a slow light sweep across it, so the eye lands on it. */
@Composable
private fun ShiningButton(modifier: Modifier, onClick: () -> Unit) {
    val sweep by rememberInfiniteTransition(label = "shine").animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(3_200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )
    Box(modifier.fillMaxWidth()) {
        PrimaryButton(text = "Get started", onClick = onClick, modifier = Modifier.fillMaxWidth())
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(SqueezeShape.TileRadius))
                .background(
                    Brush.horizontalGradient(
                        (sweep - 0.15f).coerceIn(0f, 1f) to Color.Transparent,
                        sweep.coerceIn(0f, 1f) to Color.White.copy(alpha = 0.22f),
                        (sweep + 0.15f).coerceIn(0f, 1f) to Color.Transparent,
                    ),
                ),
        )
    }
}

/**
 * The intro clip, looping and cropped to fill the screen like `object-fit: cover`.
 *
 * Plain MediaPlayer on a TextureView: one short looping file needs nothing more, and it keeps
 * the app free of a video library. Paused with the app, released with the screen.
 */
@Composable
private fun IntroVideo(volume: Float, modifier: Modifier) {
    val context = LocalContext.current
    val player = remember {
        MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            context.resources.openRawResourceFd(R.raw.intro).use { fd ->
                setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
            isLooping = true
            setVolume(0f, 0f)
        }
    }
    var prepared by remember { mutableStateOf(false) }
    val currentVolume by rememberUpdatedState(volume)

    LaunchedEffect(volume, prepared) {
        if (prepared) player.setVolume(volume, volume)
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> if (prepared && player.isPlaying) player.pause()
                Lifecycle.Event.ON_RESUME -> if (prepared && !player.isPlaying) player.start()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runCatching { player.stop() }
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                        player.setSurface(Surface(texture))
                        player.setOnVideoSizeChangedListener { _, vw, vh -> cover(this@apply, vw, vh) }
                        player.setOnPreparedListener {
                            prepared = true
                            cover(this@apply, it.videoWidth, it.videoHeight)
                            it.setVolume(currentVolume, currentVolume)
                            it.start()
                        }
                        runCatching { player.prepareAsync() }
                    }

                    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                        if (prepared) cover(this@apply, player.videoWidth, player.videoHeight)
                    }

                    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean = true

                    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                }
            }
        },
    )
}

/** Scales the video to fill the view, cropping the overflow evenly on both sides. */
private fun cover(view: TextureView, videoWidth: Int, videoHeight: Int) {
    val w = view.width.toFloat()
    val h = view.height.toFloat()
    if (w == 0f || h == 0f || videoWidth == 0 || videoHeight == 0) return
    val scale = maxOf(w / videoWidth, h / videoHeight)
    val m = Matrix()
    m.setScale(videoWidth * scale / w, videoHeight * scale / h, w / 2, h / 2)
    view.setTransform(m)
}

private fun animationsOff(context: Context): Boolean =
    runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

private fun ringerSilent(context: Context): Boolean {
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
    return audio.ringerMode != AudioManager.RINGER_MODE_NORMAL
}
