import com.alananasss.kittytune.audio.AudioEngine;
import com.alananasss.kittytune.ui.player.AudioEffectsState;
import com.alananasss.kittytune.ui.player.EqualizerState;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
public class EngineProbe {
    public static void main(String[] args) {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        bean.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        for (int i=0; i<26; i++) {
            long bytes = bean.getThreadAllocatedBytes(thread);
            long cpu = bean.getCurrentThreadCpuTime();
            long start = System.nanoTime();
            AudioEngine engine = new AudioEngine(true);
            engine.applyEffects(new AudioEffectsState());
            engine.applyEqualizer(new EqualizerState());
            engine.dspStages();
            long elapsed = System.nanoTime()-start;
            cpu=bean.getCurrentThreadCpuTime()-cpu;
            bytes=bean.getThreadAllocatedBytes(thread)-bytes;
            System.out.println("RESULT\t"+i+"\t"+elapsed+"\t"+cpu+"\t"+bytes);
            engine.release();
        }
    }
}
