package modex;

import java.nio.file.Paths;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Sin argumentos abre la interfaz grafica.
 * Modo consola:  java -jar ModEx.jar entrada.txt salida.txt [FB|V|PD]
 * Pruebas:       java -jar ModEx.jar --pruebas
 */
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            SwingUtilities.invokeLater(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) { }
                new ModexGUI().setVisible(true);
            });
            return;
        }
        if (args[0].equals("--pruebas")) {
            System.exit(Pruebas.ejecutar() ? 0 : 1);
        }
        if (args[0].equals("--benchmark-voraz")) {
            BenchmarkModex.ejecutar();
            return;
        }
        if (args.length < 2) {
            System.out.println("Uso: java -jar ModEx.jar entrada.txt salida.txt [FB|V|PD]\n"
                    + "     java -jar ModEx.jar            (abre la interfaz)\n"
                    + "     java -jar ModEx.jar --pruebas\n"
                    + "     java -jar ModEx.jar --benchmark-voraz");
            return;
        }
        RedSocial rs = ArchivoIO.leerEntrada(Paths.get(args[0]));
        String alg = args.length > 2 ? args[2].toUpperCase() : "PD";
        Solucion s;
        switch (alg) {
            case "FB": s = Modex.modexFB(rs); break;
            case "V":  s = Modex.modexV(rs); break;
            case "PD": s = Modex.modexPD(rs); break;
            default:
                System.out.println("Algoritmo desconocido: " + alg + " (use FB, V o PD)");
                return;
        }
        ArchivoIO.escribirSalida(Paths.get(args[1]), s);
        System.out.printf(java.util.Locale.US, "%s: Ext=%.6f Esfuerzo=%d (Rmax=%d) tiempo=%.3f ms%n",
                s.algoritmo, s.extremismo, s.esfuerzo, rs.rmax, s.milisegundos());
    }
}
