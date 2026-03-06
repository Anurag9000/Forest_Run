import os
import wave
import math
import struct
import random

SAMPLE_RATE = 44100

def save_wav(path, samples):
    with wave.open(path, 'w') as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(SAMPLE_RATE)
        packed = bytearray()
        for s in samples:
            s_clamp = max(-1.0, min(1.0, s))
            packed.extend(struct.pack('<h', int(s_clamp * 32767)))
        f.writeframesraw(packed)

def synth(duration, freq_func, vol_func, wave_type='sine'):
    samples = []
    num_samples = int(duration * SAMPLE_RATE)
    phase = 0.0
    for i in range(num_samples):
        t = i / num_samples
        freq = freq_func(t)
        vol = vol_func(t)
        
        phase += freq * 2 * math.pi / SAMPLE_RATE
        
        if wave_type == 'sine':
            val = math.sin(phase)
        elif wave_type == 'square':
            val = 1.0 if math.sin(phase) > 0 else -1.0
        elif wave_type == 'saw':
            val = 2.0 * (phase / (2 * math.pi) - math.floor(phase / (2 * math.pi) + 0.5))
        elif wave_type == 'noise':
            val = random.uniform(-1.0, 1.0)
            
        samples.append(val * vol)
    return samples

def ensure_dir(path):
    os.makedirs(path, exist_ok=True)

OUT_DIR = "app/src/main/res/raw"
ensure_dir(OUT_DIR)

print("Generating SFX...")

# Jump: fast sweep up
save_wav(f"{OUT_DIR}/sfx_jump.wav", synth(0.15, lambda t: 300 + 500*t, lambda t: 0.5*(1-t), 'square'))

# Land: short low noise
save_wav(f"{OUT_DIR}/sfx_land.wav", synth(0.1, lambda t: 100, lambda t: 0.8*(1-t)**2, 'noise'))

# Seed ping: high bell
save_wav(f"{OUT_DIR}/sfx_seed_ping.wav", synth(0.3, lambda t: 1200, lambda t: 0.6*(1-t)**3, 'sine'))

# Bark: short mid noise/saw
save_wav(f"{OUT_DIR}/sfx_bark.wav", synth(0.2, lambda t: 200, lambda t: 0.8*(1-t), 'saw'))

# Screech: high noise sweep
save_wav(f"{OUT_DIR}/sfx_screech.wav", synth(0.4, lambda t: 2000 - 1000*t, lambda t: 0.7*(1-t), 'noise'))

# Howl: slow sweep up and down
save_wav(f"{OUT_DIR}/sfx_howl.wav", synth(1.0, lambda t: 300 + 200*math.sin(t*math.pi), lambda t: 0.8 * math.sin(t*math.pi), 'sine'))

# Bloom: major swell
def bloom_swell(t): return 0.6 * math.sin(t*math.pi)
s_bloom = [sum(x) for x in zip(
    synth(1.5, lambda t: 440, bloom_swell, 'sine'),
    synth(1.5, lambda t: 554.37, bloom_swell, 'sine'), # C#
    synth(1.5, lambda t: 659.25, bloom_swell, 'sine')  # E
)]
save_wav(f"{OUT_DIR}/sfx_bloom.wav", s_bloom)

# Mercy miss: double beep
beep1 = synth(0.1, lambda t: 880, lambda t: 0.6*(1-t), 'square')
beep2 = synth(0.1, lambda t: 1108, lambda t: 0.6*(1-t), 'square')
silence = [0.0] * int(0.05 * SAMPLE_RATE)
save_wav(f"{OUT_DIR}/sfx_mercy_miss.wav", beep1 + silence + beep2)

# Hit: harsh low noise
save_wav(f"{OUT_DIR}/sfx_hit.wav", synth(0.4, lambda t: 100 - 50*t, lambda t: 0.9*(1-t)**2, 'saw'))


print("Generating Music...")

# Music garden: slow relaxing drone
save_wav(f"{OUT_DIR}/music_garden.wav", synth(4.0, lambda t: 220 + 5*math.sin(t*math.pi*2), lambda t: 0.3, 'sine'))

# Music run 1: slow beat
save_wav(f"{OUT_DIR}/music_run_1.wav", synth(2.0, lambda t: 60, lambda t: 0.4 * abs(math.sin(t*math.pi*4)), 'saw'))

# Music run 2: faster beat
save_wav(f"{OUT_DIR}/music_run_2.wav", synth(2.0, lambda t: 80, lambda t: 0.4 * abs(math.sin(t*math.pi*8)), 'square'))

# Music run 3: erratic
save_wav(f"{OUT_DIR}/music_run_3.wav", synth(2.0, lambda t: 120 + 20*math.sin(t*math.pi*16), lambda t: 0.4 * abs(math.sin(t*math.pi*16)), 'saw'))

# Music bloom: triumphant loop
s_bloom_music = [sum(x) for x in zip(
    synth(2.0, lambda t: 440, lambda t: 0.3*abs(math.sin(t*math.pi*8)), 'square'),
    synth(2.0, lambda t: 659.25, lambda t: 0.3*abs(math.sin(t*math.pi*8)), 'square')
)]
save_wav(f"{OUT_DIR}/music_bloom.wav", s_bloom_music)

# Music rest: sad drone
save_wav(f"{OUT_DIR}/music_rest.wav", synth(4.0, lambda t: 164.81, lambda t: 0.3, 'sine'))

print("Done! Audio saved to res/raw/")
