#!/usr/bin/env python3
"""Upgrade SaveManager ghost persistence to a versioned stable-state format."""

from pathlib import Path

PATH = Path("app/src/main/java/com/anurag9000/forestrun/engine/SaveManager.kt")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    text = PATH.read_text(encoding="utf-8")

    text = replace_once(
        text,
        "import com.anurag9000.forestrun.systems.GhostRecorder\n",
        "import com.anurag9000.forestrun.systems.GhostRecorder\n"
        "import com.anurag9000.forestrun.systems.GhostStateCodec\n",
        "GhostStateCodec import",
    )

    text = replace_once(
        text,
        '''    private const val GHOST_HEADER_BYTES = 4L
    private const val GHOST_FRAME_BYTES = 24L
    private val MAX_GHOST_FILE_BYTES =
        GHOST_HEADER_BYTES + GhostRecorder.MAX_FRAMES.toLong() * GHOST_FRAME_BYTES
''',
        '''    internal const val GHOST_FILE_MAGIC = 0x46524748 // "FRGH"
    internal const val GHOST_FILE_VERSION = 2
    private const val LEGACY_GHOST_HEADER_BYTES = 4L
    private const val VERSIONED_GHOST_HEADER_BYTES = 12L
    private const val GHOST_FRAME_BYTES = 24L
    private val MAX_GHOST_FILE_BYTES =
        VERSIONED_GHOST_HEADER_BYTES + GhostRecorder.MAX_FRAMES.toLong() * GHOST_FRAME_BYTES
''',
        "ghost format constants",
    )

    text = replace_once(
        text,
        '''            val output = DataOutputStream(BufferedOutputStream(stream))
            output.writeInt(frames.size)
            for (frame in frames) {
                output.writeFloat(frame.t)
                output.writeFloat(frame.x)
                output.writeFloat(frame.y)
                output.writeInt(frame.stateOrdinal)
                output.writeFloat(frame.scaleX)
                output.writeFloat(frame.scaleY)
            }
''',
        '''            val output = DataOutputStream(BufferedOutputStream(stream))
            output.writeInt(GHOST_FILE_MAGIC)
            output.writeInt(GHOST_FILE_VERSION)
            output.writeInt(frames.size)
            for (frame in frames) {
                output.writeFloat(frame.t)
                output.writeFloat(frame.x)
                output.writeFloat(frame.y)
                output.writeInt(requireNotNull(GhostStateCodec.encodeOrdinal(frame.stateOrdinal)))
                output.writeFloat(frame.scaleX)
                output.writeFloat(frame.scaleY)
            }
''',
        "versioned ghost writer",
    )

    text = replace_once(
        text,
        '''            if (fileSize !in GHOST_HEADER_BYTES..MAX_GHOST_FILE_BYTES) {
                input.close()
                return emptyList()
            }

            DataInputStream(input.buffered()).use { data ->
                val count = data.readInt()
                if (count !in 1..GhostRecorder.MAX_FRAMES) return emptyList()

                val expectedBytes = GHOST_HEADER_BYTES + count.toLong() * GHOST_FRAME_BYTES
                if (fileSize != expectedBytes) return emptyList()

                val frames = ArrayList<GhostFrame>(count)
                var previousTime = Float.NEGATIVE_INFINITY
                repeat(count) {
                    val frame = GhostFrame(
                        t = data.readFloat(),
                        x = data.readFloat(),
                        y = data.readFloat(),
                        stateOrdinal = data.readInt(),
                        scaleX = data.readFloat(),
                        scaleY = data.readFloat()
                    )
                    if (!isValidGhostFrame(frame, previousTime)) return emptyList()
                    previousTime = frame.t
                    frames.add(frame)
                }
                frames
            }
''',
        '''            if (fileSize !in LEGACY_GHOST_HEADER_BYTES..MAX_GHOST_FILE_BYTES) {
                input.close()
                return emptyList()
            }

            DataInputStream(input.buffered()).use { data ->
                val firstWord = data.readInt()
                val isVersioned = firstWord == GHOST_FILE_MAGIC
                val headerBytes: Long
                val count: Int
                if (isVersioned) {
                    val version = data.readInt()
                    if (version != GHOST_FILE_VERSION) return emptyList()
                    count = data.readInt()
                    headerBytes = VERSIONED_GHOST_HEADER_BYTES
                } else {
                    // Legacy v1 files stored only count + raw enum ordinals.
                    count = firstWord
                    headerBytes = LEGACY_GHOST_HEADER_BYTES
                }
                if (count !in 1..GhostRecorder.MAX_FRAMES) return emptyList()

                val expectedBytes = headerBytes + count.toLong() * GHOST_FRAME_BYTES
                if (fileSize != expectedBytes) return emptyList()

                val frames = ArrayList<GhostFrame>(count)
                var previousTime = Float.NEGATIVE_INFINITY
                repeat(count) {
                    val t = data.readFloat()
                    val x = data.readFloat()
                    val y = data.readFloat()
                    val storedState = data.readInt()
                    val stateOrdinal = if (isVersioned) {
                        GhostStateCodec.decodeToOrdinal(storedState) ?: return emptyList()
                    } else {
                        storedState
                    }
                    val frame = GhostFrame(
                        t = t,
                        x = x,
                        y = y,
                        stateOrdinal = stateOrdinal,
                        scaleX = data.readFloat(),
                        scaleY = data.readFloat()
                    )
                    if (!isValidGhostFrame(frame, previousTime)) return emptyList()
                    previousTime = frame.t
                    frames.add(frame)
                }
                frames
            }
''',
        "versioned and legacy ghost reader",
    )

    PATH.write_text(text, encoding="utf-8")
    print("Patched SaveManager ghost persistence to version 2")


if __name__ == "__main__":
    main()
