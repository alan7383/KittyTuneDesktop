import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;

public final class StartupBenchAgent {
    public static void premain(String mode, Instrumentation instrumentation) {
        boolean skip = "skip".equals(mode);
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> type,
                    ProtectionDomain domain, byte[] bytes) {
                boolean zapret = name.equals("com/alananasss/kittytune/data/zapret/ZapretManager");
                boolean player = name.equals("com/alananasss/kittytune/ui/player/PlayerViewModel");
                if (!zapret && !player) return null;
                ClassReader reader = new ClassReader(bytes);
                ClassWriter writer = new ClassWriter(0);
                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    public MethodVisitor visitMethod(int access, String method, String descriptor,
                            String signature, String[] exceptions) {
                        MethodVisitor mv = super.visitMethod(access, method, descriptor, signature, exceptions);
                        boolean media = player && method.equals("initWindowsSmtcService") && descriptor.equals("()V");
                        boolean configure = zapret && method.equals("autoConfigureOnce");
                        boolean discovery = zapret && method.equals("detect") && descriptor.equals("()Ljava/io/File;") && skip;
                        if (!media && !configure && !discovery) return mv;
                        System.err.println("BENCH isolation: " + name + "." + method + " mode=" + mode);
                        mv.visitCode();
                        if (media) mv.visitInsn(Opcodes.RETURN);
                        else if (discovery) { mv.visitInsn(Opcodes.ACONST_NULL); mv.visitInsn(Opcodes.ARETURN); }
                        else {
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC,"java/util/Collections","emptyList","()Ljava/util/List;",false);
                            mv.visitInsn(Opcodes.ARETURN);
                        }
                        mv.visitMaxs(media ? 0 : 1, (access & Opcodes.ACC_STATIC) == 0 ? Type.getArgumentTypes(descriptor).length + 1 : Type.getArgumentTypes(descriptor).length);
                        mv.visitEnd();
                        return null;
                    }
                },0);
                return writer.toByteArray();
            }
        });
    }
}
