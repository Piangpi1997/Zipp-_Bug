package com.zipbug.base.zippyweb

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Turns the Zippy Web Studio "master clock" render plan (built by the
 * transplanted web UI and passed to AndroidBridge.startRender) into a real
 * FFmpeg argument list.
 *
 * Plan shape (version 1):
 * {version, master:'audio', audioDuration, sourceVideoDuration,
 *  clipSourceSeconds, lockedSpeed, slotSeconds, transitionSeconds,
 *  clips:[{id,start,end}], links:[{audioTime,clipId,order}],
 *  slotFx:{slotIndex:{...}}, transitions:[{from,to,style,duration}]}
 *
 * Semantics: the master audio is divided into slotSeconds-long slots; each
 * linked slot plays clipSourceSeconds of its clip slowed/sped by the speed
 * factor so it fills the slot. Effects honoured in v1: speed, reverse,
 * flip, freeze, zoom, fade transitions (as fade-through-black so the total
 * duration stays exact). Any other effect group present in the plan is
 * reported in [ZippyRenderJob.skippedFx] instead of being silently dropped.
 */
object ZippyRenderPlanner {

    data class ZippyRenderJob(
        val args: List<String>,
        val totalSeconds: Double,
        val appliedFx: List<String>,
        val skippedFx: List<String>
    )

    private val KNOWN_FX = setOf("speed", "reverse", "flip", "freeze", "zoom")

    fun build(
        planJson: String,
        videoPath: String,
        audioPath: String,
        outputPath: String,
        width: Int,
        height: Int
    ): ZippyRenderJob {
        val plan = JSONObject(planJson)
        val slotSeconds = plan.optDouble("slotSeconds", 5.0)
        val clipSourceSeconds = plan.optDouble("clipSourceSeconds", 2.5)
        val lockedSpeed = plan.optDouble("lockedSpeed", 0.5)
        val transitionSeconds = plan.optDouble("transitionSeconds", 2.0)
        val audioDuration = plan.optDouble("audioDuration", 0.0)
        require(slotSeconds > 0) { "Plan has no valid slotSeconds" }
        require(lockedSpeed > 0) { "Plan has no valid lockedSpeed" }
        require(width >= 16 && height >= 16) { "Invalid output size ${width}x$height" }

        val clips = HashMap<String, JSONObject>()
        val clipArr = plan.optJSONArray("clips") ?: JSONArray()
        for ( i in 0 until clipArr.length() ) {
            val c = clipArr.getJSONObject(i)
            clips[c.optString("id")] = c
        }
        val linkArr = plan.optJSONArray("links") ?: JSONArray()
        require(linkArr.length() > 0) { "Plan has no audio-to-clip links" }
        val links = ArrayList<JSONObject>()
        for ( i in 0 until linkArr.length() ) links += linkArr.getJSONObject(i)
        links.sortBy { it.optInt("order", 0) }

        val slotFx = plan.optJSONObject("slotFx") ?: JSONObject()
        val fadeSlots = HashSet<Int>()
        val transArr = plan.optJSONArray("transitions") ?: JSONArray()
        for ( i in 0 until transArr.length() ) {
            val t = transArr.getJSONObject(i)
            if (t.optString("style") == "fade") {
                fadeSlots += t.optInt("from", -1)
                fadeSlots += t.optInt("to", -1)
            }
        }

        val applied = sortedSetOf<String>()
        val skipped = sortedSetOf<String>()
        val filter = StringBuilder()
        val segmentLabels = ArrayList<String>()

        links.forEachIndexed { slot, link ->
            val clip = clips[link.optString("clipId")]
                ?: throw IllegalArgumentException("Plan link $slot references an unknown clip")
            val start = clip.optDouble("start", 0.0)
            val end = clip.optDouble("end", start + clipSourceSeconds)
            val available = (end - start).coerceAtLeast(0.1)
            val fx = slotFx.optJSONObject(slot.toString()) ?: JSONObject()
            val names = fx.names()
            if (names != null) {
                for ( n in 0 until names.length() ) {
                    val key = names.getString(n)
                    if (key in KNOWN_FX) applied += key else skipped += key
                }
            }
            val speed = if (fx.has("speed")) fx.optDouble("speed", lockedSpeed) else lockedSpeed
            require(speed > 0) { "Slot $slot has an invalid speed" }
            val sourceNeeded = slotSeconds * speed
            val take = minOf(clipSourceSeconds, available, sourceNeeded).coerceAtLeast(0.1)

            val chain = StringBuilder("[0:v]trim=start=${f(start)}:duration=${f(take)},setpts=PTS-STARTPTS")
            if (fx.optBoolean("reverse", false)) chain.append(",reverse")
            chain.append(",setpts=PTS/${f(speed)}")
            val zoom = fx.optDouble("zoom", 1.0)
            if (zoom > 1.01) {
                chain.append(",crop='trunc(iw/${f(zoom)}/2)*2':'trunc(ih/${f(zoom)}/2)*2'")
                applied += "zoom"
            }
            chain.append(",scale=$width:$height:force_original_aspect_ratio=decrease")
            chain.append(",pad=$width:$height:(ow-iw)/2:(oh-ih)/2")
            when (val flip = fx.opt("flip")) {
                is String -> {
                    if (flip.contains("v")) chain.append(",vflip") else chain.append(",hflip")
                }
                is Boolean -> if (flip) chain.append(",hflip")
            }
            if (fx.has("freeze") && fx.opt("freeze") != JSONObject.NULL) {
                chain.append(",tpad=stop_mode=clone:stop_duration=${f(slotSeconds)}")
            }
            chain.append(",trim=duration=${f(slotSeconds)},setpts=PTS-STARTPTS")
            if (slot in fadeSlots) {
                val fd = (transitionSeconds / 2).coerceAtMost(slotSeconds / 2)
                chain.append(",fade=t=in:st=0:d=${f(fd)}")
                chain.append(",fade=t=out:st=${f(slotSeconds - fd)}:d=${f(fd)}")
                applied += "fade"
            }
            chain.append(",fps=30,format=yuv420p,setsar=1")
            chain.append("[v$slot]")
            filter.append(chain).append(";")
            segmentLabels += "[v$slot]"
        }

        val slotTotal = slotSeconds * links.size
        val total = if (audioDuration > 0) minOf(audioDuration, slotTotal) else slotTotal
        filter.append(segmentLabels.joinToString(""))
        filter.append("concat=n=${links.size}:v=1:a=0[vcat];")
        filter.append("[vcat]trim=duration=${f(total)},setpts=PTS-STARTPTS[vout];")
        filter.append("[1:a]atrim=start=0:duration=${f(total)},asetpts=PTS-STARTPTS[aout]")

        val args = listOf(
            "-y", "-i", videoPath, "-i", audioPath,
            "-filter_complex", filter.toString(),
            "-map", "[vout]", "-map", "[aout]",
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
            "-c:a", "aac", "-b:a", "160k",
            "-movflags", "+faststart", outputPath
        )
        return ZippyRenderJob(args, total, applied.toList(), skipped.toList())
    }

    private fun f(v: Double): String = String.format(Locale.US, "%.3f", v)
}
