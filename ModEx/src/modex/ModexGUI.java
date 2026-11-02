package modex;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

/** Interfaz: cargar/generar entradas, verlas, ejecutar FB / Voraz / PD, revisar y guardar salidas. */
public class ModexGUI extends JFrame {
    private RedSocial red;
    private Solucion sol;
    private String nombreArchivo = "(ninguno)";
    private File dirActual = new File(System.getProperty("user.dir"));

    private final DefaultTableModel modelo;
    private final JTable tabla;
    private final JTextArea texto = new JTextArea();
    private final JLabel info = new JLabel(" Cargue un archivo de entrada o genere un caso de prueba.");
    private final JComboBox<String> combo = new JComboBox<>(new String[]{
            "Fuerza bruta (modexFB)", "Voraz (modexV)", "Programacion dinamica (modexPD)"});
    private final List<JButton> botones = new ArrayList<>();

    private static final class Comparacion {
        final List<Solucion> sols = new ArrayList<>();
        final List<String> notas = new ArrayList<>();
    }

    public ModexGUI() {
        super("ModEx - Moderando el extremismo de opiniones en una red social");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1150, 760);
        setLocationRelativeTo(null);

        modelo = new DefaultTableModel(new String[]{"Agente", "Opinion", "Receptividad", "Influencia",
                "Costo de moderar", "Aporte o^2*inf", "Moderado", "Opinion final"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tabla = new JTable(modelo) {
            @Override public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                if (!isRowSelected(row)) {
                    Object v = getModel().getValueAt(convertRowIndexToModel(row), 6);
                    c.setBackground("Si".equals(v) ? new Color(198, 232, 198) : Color.WHITE);
                    c.setForeground(Color.BLACK);
                }
                return c;
            }
        };
        tabla.setRowHeight(22);
        tabla.setAutoCreateRowSorter(true);

        texto.setEditable(false);
        texto.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        barra.add(boton("Cargar entrada...", e -> cargar()));
        barra.add(boton("Generar caso...", e -> generar()));
        barra.add(new JLabel("  Algoritmo:"));
        barra.add(combo);
        barra.add(boton("Ejecutar", e -> ejecutar()));
        barra.add(boton("Comparar los 3", e -> comparar()));
        barra.add(boton("Verificar costo", e -> verificar()));
        barra.add(boton("Guardar salida...", e -> guardar()));
        barra.add(boton("Cargar salida a verificar...", e -> cargarSalida()));

        JPanel norte = new JPanel(new BorderLayout());
        norte.add(barra, BorderLayout.NORTH);
        norte.add(info, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tabla), new JScrollPane(texto));
        split.setResizeWeight(0.55);
        getContentPane().add(norte, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);
    }

    private JButton boton(String t, java.awt.event.ActionListener l) {
        JButton b = new JButton(t);
        b.addActionListener(l);
        botones.add(b);
        return b;
    }

    private void setBotones(boolean on) {
        for (JButton b : botones) b.setEnabled(on);
    }

    private void error(String m) {
        JOptionPane.showMessageDialog(this, m, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.US, "%.2f", d);
    }

    /** Ejecuta una tarea pesada fuera del hilo de la interfaz. */
    private <T> void trabajar(Callable<T> tarea, Consumer<T> fin) {
        setBotones(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return tarea.call(); }
            @Override protected void done() {
                setBotones(true);
                setCursor(Cursor.getDefaultCursor());
                try {
                    fin.accept(get());
                } catch (ExecutionException ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    error(c instanceof OutOfMemoryError
                            ? "Memoria insuficiente. Ejecute con mas heap: java -Xmx4g -jar ModEx.jar"
                            : String.valueOf(c.getMessage()));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------ entrada
    private void mostrarEntrada() {
        modelo.setRowCount(0);
        sol = null;
        for (int i = 0; i < red.n; i++)
            modelo.addRow(new Object[]{i, fmt(red.op[i]), fmt(red.rec[i]), fmt(red.inf[i]), red.costo[i],
                    String.format(Locale.US, "%.1f", red.aporte[i]), "-", "-"});
        info.setText(String.format(Locale.US, " Archivo: %s   |   n = %d   |   Rmax = %d   |   Ext original = %.6f",
                nombreArchivo, red.n, red.rmax, red.extremismoOriginal()));
    }

    private void cargar() {
        JFileChooser fc = new JFileChooser(dirActual);
        fc.setDialogTitle("Seleccione el archivo de entrada");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        dirActual = fc.getCurrentDirectory();
        try {
            red = ArchivoIO.leerEntrada(fc.getSelectedFile().toPath());
            nombreArchivo = fc.getSelectedFile().getName();
            mostrarEntrada();
            texto.setText("Entrada cargada correctamente.\nAgentes: " + red.n + "\nRmax: " + red.rmax + "\n");
        } catch (Exception ex) {
            error("No se pudo leer la entrada:\n" + ex.getMessage());
        }
    }

    private void generar() {
        JTextField fn = new JTextField("10"), fs = new JTextField("1"), ff = new JTextField("0.5");
        Object[] msg = {"Numero de agentes n:", fn, "Semilla aleatoria:", fs,
                "Rmax como fraccion (0,1] del esfuerzo de moderar a todos:", ff};
        if (JOptionPane.showConfirmDialog(this, msg, "Generar caso de prueba",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            int n = Integer.parseInt(fn.getText().trim());
            long seed = Long.parseLong(fs.getText().trim());
            double frac = Double.parseDouble(ff.getText().trim().replace(',', '.'));
            if (n < 1 || frac <= 0 || frac > 1) throw new NumberFormatException();
            red = Generador.generar(n, seed, frac);
            nombreArchivo = "generado(n=" + n + ", semilla=" + seed + ")";
            mostrarEntrada();
            texto.setText("Caso generado. Puede guardarlo como archivo de entrada.\n");
            JFileChooser fc = new JFileChooser(dirActual);
            fc.setDialogTitle("Guardar el caso generado como archivo de entrada");
            fc.setSelectedFile(new File(dirActual, "entrada_n" + n + "_s" + seed + ".txt"));
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                dirActual = fc.getCurrentDirectory();
                ArchivoIO.escribirEntrada(fc.getSelectedFile().toPath(), red);
                nombreArchivo = fc.getSelectedFile().getName();
                mostrarEntrada();
                texto.append("Guardado en " + fc.getSelectedFile().getAbsolutePath() + "\n");
            }
        } catch (NumberFormatException ex) {
            error("Valores invalidos. n >= 1, semilla entera y fraccion en (0,1].");
        } catch (Exception ex) {
            error("No se pudo guardar: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------ ejecucion
    private Solucion correr(int alg) {
        switch (alg) {
            case 0: return Modex.modexFB(red);
            case 1: return Modex.modexV(red);
            default: return Modex.modexPD(red);
        }
    }

    private boolean hayEntrada() {
        if (red == null) {
            JOptionPane.showMessageDialog(this, "Primero cargue o genere una entrada.");
            return false;
        }
        return true;
    }

    private boolean confirmarFB() {
        if (red.n > 24) {
            return JOptionPane.showConfirmDialog(this, "Fuerza bruta con n = " + red.n
                    + " revisa 2^" + red.n + " subconjuntos y puede tardar mucho. Continuar?",
                    "Advertencia", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
        }
        return true;
    }

    private void mostrarSolucion(Solucion s) {
        sol = s;
        for (int i = 0; i < red.n; i++) {
            modelo.setValueAt(s.e[i] == 1 ? "Si" : "No", i, 6);
            modelo.setValueAt(s.e[i] == 1 ? "0" : fmt(red.op[i]), i, 7);
        }
    }

    private String resumen(Solucion s) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(s.algoritmo).append(" ===\n");
        sb.append(String.format(Locale.US, "Tiempo de ejecucion : %.3f ms%n", s.milisegundos()));
        sb.append(String.format(Locale.US, "Ext original        : %.6f%n", red.extremismoOriginal()));
        sb.append(String.format(Locale.US, "Ext red moderada    : %.6f%n", s.extremismo));
        sb.append(String.format(Locale.US, "Esfuerzo            : %d  (Rmax = %d)  -> %s%n", s.esfuerzo, red.rmax,
                s.esfuerzo <= red.rmax ? "APLICABLE" : "NO APLICABLE"));
        sb.append("Estrategia E        : ");
        StringBuilder e = new StringBuilder("<");
        StringBuilder mod = new StringBuilder();
        for (int i = 0; i < red.n; i++) {
            e.append(s.e[i]).append(i < red.n - 1 ? "," : "");
            if (s.e[i] == 1) mod.append(mod.length() > 0 ? ", " : "").append(i);
        }
        sb.append(e).append(">\n");
        sb.append("Agentes moderados   : ").append(mod.length() > 0 ? mod : "(ninguno)").append("\n");
        return sb.toString();
    }

    private void ejecutar() {
        if (!hayEntrada()) return;
        final int alg = combo.getSelectedIndex();
        if (alg == 0 && !confirmarFB()) return;
        trabajar(() -> correr(alg), s -> {
            mostrarSolucion(s);
            texto.setText(resumen(s) + "\nUse 'Verificar costo' para ver el desglose del calculo.\n");
        });
    }

    private void comparar() {
        if (!hayEntrada()) return;
        final boolean conFB = red.n <= Modex.MAX_FB && (red.n <= 24 || confirmarFB());
        trabajar(() -> {
            Comparacion c = new Comparacion();
            if (conFB) c.sols.add(Modex.modexFB(red));
            else c.notas.add("Fuerza bruta omitida (n = " + red.n + ").");
            c.sols.add(Modex.modexV(red));
            try {
                c.sols.add(Modex.modexPD(red));
            } catch (IllegalArgumentException | OutOfMemoryError ex) {
                c.notas.add("Programacion dinamica omitida: " + ex.getMessage());
            }
            return c;
        }, c -> {
            double mejor = Double.MAX_VALUE;
            Solucion mejorS = null;
            for (Solucion s : c.sols)
                if (s.extremismo < mejor - 1e-12) { mejor = s.extremismo; mejorS = s; }
            StringBuilder sb = new StringBuilder("=== Comparacion de los tres enfoques ===\n");
            sb.append(String.format("%-24s %-14s %-10s %-14s %s%n", "Algoritmo", "Extremismo", "Esfuerzo",
                    "Tiempo (ms)", "Dif. vs mejor"));
            for (Solucion s : c.sols) {
                double gap = mejor > 1e-12 ? (s.extremismo - mejor) / mejor * 100 : 0;
                sb.append(String.format(Locale.US, "%-24s %-14.6f %-10d %-14.3f %.4f%%%n", s.algoritmo,
                        s.extremismo, s.esfuerzo, s.milisegundos(), Math.abs(gap) < 1e-9 ? 0 : gap));
            }
            for (String nota : c.notas) sb.append("* ").append(nota).append("\n");
            texto.setText(sb.toString());
            if (mejorS != null) mostrarSolucion(mejorS);
        });
    }

    // ------------------------------------------------------------ verificacion
    private void verificar() {
        if (!hayEntrada()) return;
        if (sol == null) {
            JOptionPane.showMessageDialog(this, "Primero ejecute un algoritmo o cargue una salida.");
            return;
        }
        long total = 0;
        StringBuilder sb = new StringBuilder(resumen(sol));
        sb.append("\n--- Verificacion del esfuerzo: suma de ceil(|o| * (1 - r) * inf) de los moderados ---\n");
        for (int i = 0; i < red.n; i++) {
            if (sol.e[i] != 1) continue;
            double raw = RedSocial.costoReal(red.op[i], red.rec[i], red.inf[i]);
            sb.append(String.format(Locale.US, "agente %d: ceil(|%s| * (1 - %s) * %s) = ceil(%.4f) = %d%n", i,
                    fmt(red.op[i]), fmt(red.rec[i]), fmt(red.inf[i]), raw, red.costo[i]));
            total += red.costo[i];
        }
        sb.append(String.format("Esfuerzo recalculado = %d   %s   Rmax = %d  -> %s%n", total,
                total <= red.rmax ? "<=" : ">", red.rmax, total <= red.rmax ? "APLICABLE" : "NO APLICABLE"));
        double s = red.sumaNoModerada(sol.e);
        sb.append("\n--- Verificacion del extremismo: Ext = raiz( suma o'^2 * inf ) / n ---\n");
        sb.append(String.format(Locale.US, "Suma de o^2*inf de los NO moderados = %.4f%n", s));
        sb.append(String.format(Locale.US, "Ext = sqrt(%.4f) / %d = %.6f%n", s, red.n, red.n == 0 ? 0 : Math.sqrt(s) / red.n));
        texto.setText(sb.toString());
    }

    private void guardar() {
        if (!hayEntrada()) return;
        if (sol == null) {
            JOptionPane.showMessageDialog(this, "No hay una salida calculada para guardar.");
            return;
        }
        JFileChooser fc = new JFileChooser(dirActual);
        fc.setDialogTitle("Guardar salida");
        fc.setSelectedFile(new File(dirActual, "salida.txt"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        dirActual = fc.getCurrentDirectory();
        try {
            ArchivoIO.escribirSalida(fc.getSelectedFile().toPath(), sol);
            texto.append("\nSalida guardada en " + fc.getSelectedFile().getAbsolutePath() + "\n");
        } catch (Exception ex) {
            error("No se pudo guardar: " + ex.getMessage());
        }
    }

    private void cargarSalida() {
        if (!hayEntrada()) return;
        JFileChooser fc = new JFileChooser(dirActual);
        fc.setDialogTitle("Seleccione un archivo de salida para verificar");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        dirActual = fc.getCurrentDirectory();
        try {
            ArchivoIO.Salida o = ArchivoIO.leerSalida(fc.getSelectedFile().toPath(), red.n);
            Solucion s = Solucion.crear("Salida cargada (" + fc.getSelectedFile().getName() + ")", red, o.e, 0);
            mostrarSolucion(s);
            StringBuilder sb = new StringBuilder(resumen(s));
            sb.append(String.format(Locale.US, "%nDeclarado en el archivo: Ext = %.6f, Esf = %d%n", o.ext, o.esf));
            sb.append(Math.abs(o.ext - s.extremismo) < 1e-4 && o.esf == s.esfuerzo
                    ? "Los valores declarados COINCIDEN con los recalculados.\n"
                    : "ATENCION: los valores declarados NO coinciden con los recalculados.\n");
            texto.setText(sb.toString());
        } catch (Exception ex) {
            error("No se pudo leer la salida:\n" + ex.getMessage());
        }
    }
}
