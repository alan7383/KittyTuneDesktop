import com.sun.tools.attach.VirtualMachine;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Window;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class RendererProbe {
    static String quote(Object value) {
        if (value == null) return "null";
        return "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }

    static void collect(Component component, String window, ArrayList<String> layers) throws Exception {
        Class<?> type = Class.forName("org.jetbrains.skiko.SkiaLayer");
        if (type.isInstance(component)) {
            Object api = type.getMethod("getRenderApi").invoke(component);
            Object info = type.getMethod("getRenderInfo").invoke(component);
            var scale = component.getGraphicsConfiguration().getDefaultTransform();
            layers.add("{\"window\":" + quote(window) + ",\"api\":" + quote(api)
                + ",\"info\":" + quote(info) + ",\"width\":" + component.getWidth()
                + ",\"height\":" + component.getHeight() + ",\"scaleX\":" + scale.getScaleX()
                + ",\"scaleY\":" + scale.getScaleY() + "}");
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) collect(child, window, layers);
        }
    }

    public static void agentmain(String output, Instrumentation instrumentation) throws Exception {
        ArrayList<String> layers = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();
        EventQueue.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) {
                if (!window.isVisible()) continue;
                String title = window instanceof Frame frame ? frame.getTitle() : window.getName();
                try { collect(window, title, layers); }
                catch (Exception error) { errors.add(quote(error.toString())); }
            }
        });
        Files.writeString(Path.of(output), "{\"requested\":" + quote(System.getProperty("skiko.renderApi"))
            + ",\"layers\":[" + String.join(",", layers) + "],\"errors\":[" + String.join(",", errors) + "]}");
    }

    public static void main(String[] args) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(args[0]);
        try { vm.loadAgent(args[1], args[2]); }
        finally { vm.detach(); }
    }
}
