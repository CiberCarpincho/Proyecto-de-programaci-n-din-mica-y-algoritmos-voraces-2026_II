package modex;

import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
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
    private final JLabel estadoTrabajo = new JLabel(" ");
    private final JProgressBar barraProgreso = new JProgressBar(0, 100);
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

        JPanel barraResultados = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        barraResultados.add(boton("Generar carpeta de resultados...", e -> generarResultados()));

        JPanel norte = new JPanel(new BorderLayout());
        JPanel controles = new JPanel(new BorderLayout());
        controles.add(barra, BorderLayout.NORTH);
        controles.add(barraResultados, BorderLayout.SOUTH);
        norte.add(controles, BorderLayout.NORTH);
        norte.add(info, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tabla), new JScrollPane(texto));
        split.setResizeWeight(0.55);
        getContentPane().add(norte, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);
        JPanel estado = new JPanel(new BorderLayout(8, 0));
        estado.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        estado.add(estadoTrabajo, BorderLayout.CENTER);
        barraProgreso.setStringPainted(true);
        barraProgreso.setVisible(false);
        barraProgreso.setPreferredSize(new Dimension(220, 18));
        estado.add(barraProgreso, BorderLayout.EAST);
        getContentPane().add(estado, BorderLayout.SOUTH);
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
        trabajarConProgreso(progreso -> tarea.call(), fin);
    }

    @FunctionalInterface
    private interface TareaConProgreso<T> {
        T ejecutar(IntConsumer reportar) throws Exception;
    }

    private <T> void trabajarConProgreso(TareaConProgreso<T> tarea, Consumer<T> fin) {
        setBotones(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        estadoTrabajo.setText("Procesando en segundo plano…");
        barraProgreso.setValue(0);
        barraProgreso.setIndeterminate(true);
        barraProgreso.setVisible(true);
        new SwingWorker<T, Integer>() {
            @Override protected T doInBackground() throws Exception {
                return tarea.ejecutar(valor -> publish(valor));
            }
            @Override protected void process(List<Integer> avances) {
                int valor = avances.get(avances.size() - 1);
                if (valor < 0) {
                    barraProgreso.setIndeterminate(true);
                    estadoTrabajo.setText("Procesando en segundo plano…");
                } else {
                    barraProgreso.setIndeterminate(false);
                    barraProgreso.setValue(valor);
                    estadoTrabajo.setText("Programación dinámica: " + valor + "%");
                }
            }
            @Override protected void done() {
                setBotones(true);
                setCursor(Cursor.getDefaultCursor());
                barraProgreso.setIndeterminate(false);
                barraProgreso.setVisible(false);
                estadoTrabajo.setText(" ");
                try {
                    fin.accept(get());
                } catch (ExecutionException ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    error(c instanceof OutOfMemoryError
                            ? "Memoria insuficiente para esta entrada. Aumente el heap con -Xmx (por ejemplo, java -Xmx3g -jar ModEx.jar) o reduzca Rmax."
                            : String.valueOf(c.getMessage()));
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    // ------------------------------------------------------------ entrada
    private void mostrarEntrada() {
        sol = null;
        Object[][] filas=new Object[red.n][8];
        for (int i = 0; i < red.n; i++) filas[i]=new Object[]{i,fmt(red.op[i]),fmt(red.rec[i]),fmt(red.inf[i]),red.costo[i],
                String.format(Locale.US,"%.1f",red.aporte[i]),"-","-"};
        modelo.setDataVector(filas,new String[]{"Agente","Opinion","Receptividad","Influencia",
                "Costo de moderar","Aporte o^2*inf","Moderado","Opinion final"});
        info.setText(String.format(Locale.US, " Archivo: %s   |   n = %d   |   Rmax = %d   |   Ext original = %.6f",
                nombreArchivo, red.n, red.rmax, red.extremismoOriginal()));
    }

    private void cargar() {
        JFileChooser fc = new JFileChooser(dirActual);
        fc.setDialogTitle("Seleccione el archivo de entrada");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        dirActual = fc.getCurrentDirectory();
        Path archivo=fc.getSelectedFile().toPath();
        trabajar(() -> ArchivoIO.leerEntrada(archivo), nueva -> {
            red=nueva;
            nombreArchivo=archivo.getFileName().toString();
            mostrarEntrada();
            texto.setText("Entrada cargada correctamente.\nAgentes: " + red.n + "\nRmax: " + red.rmax + "\n");
        });
    }

    private void generar() {
        JTextField fn = new JTextField("10"), fs = new JTextField("1"), ff = new JTextField("0.5");
        Object[] msg = {"Numero de agentes n:", fn, "Semilla aleatoria:", fs,
                "Rmax como fraccion (0,1] del esfuerzo de moderar a todos:", ff};
        if (JOptionPane.showConfirmDialog(this, msg, "Generar caso de prueba",
                JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        final int n; final long seed; final double frac;
        try {
            n=Integer.parseInt(fn.getText().trim());
            seed=Long.parseLong(fs.getText().trim());
            frac=Double.parseDouble(ff.getText().trim().replace(',','.'));
            if(n<1||frac<=0||frac>1)throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            error("Valores invalidos. n >= 1, semilla entera y fraccion en (0,1].");
            return;
        }
        trabajar(() -> Generador.generar(n,seed,frac),nueva -> {
            red=nueva;nombreArchivo="generado(n="+n+", semilla="+seed+")";mostrarEntrada();
            texto.setText("Caso generado. Puede guardarlo como archivo de entrada.\n");
            JFileChooser fc=new JFileChooser(dirActual);
            fc.setDialogTitle("Guardar el caso generado como archivo de entrada");
            fc.setSelectedFile(new File(dirActual,"entrada_n"+n+"_s"+seed+".txt"));
            if(fc.showSaveDialog(this)==JFileChooser.APPROVE_OPTION){
                dirActual=fc.getCurrentDirectory();Path destino=fc.getSelectedFile().toPath();
                trabajar(() -> {ArchivoIO.escribirEntrada(destino,nueva);return destino;},path -> {
                    nombreArchivo=path.getFileName().toString();mostrarEntrada();
                    texto.append("Guardado en "+path.toAbsolutePath()+"\n");
                });
            }
        });
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
        if (red.n > Modex.MAX_FB) {
            JOptionPane.showMessageDialog(this,
                    "Fuerza bruta omitida por tamaño: n = " + red.n
                    + " supera el máximo de " + Modex.MAX_FB + ".",
                    "Algoritmo omitido", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        if (red.n > 24) {
            return JOptionPane.showConfirmDialog(this, "Fuerza bruta con n = " + red.n
                    + " revisa 2^" + red.n + " subconjuntos y puede tardar mucho. Continuar?",
                    "Advertencia", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
        }
        return true;
    }

    private void mostrarSolucion(Solucion s) {
        sol = s;
        Object[][] filas=new Object[red.n][8];
        for(int i=0;i<red.n;i++) filas[i]=new Object[]{i,fmt(red.op[i]),fmt(red.rec[i]),fmt(red.inf[i]),red.costo[i],
                String.format(Locale.US,"%.1f",red.aporte[i]),s.e[i]==1?"Si":"No",s.e[i]==1?"0":fmt(red.op[i])};
        modelo.setDataVector(filas,new String[]{"Agente","Opinion","Receptividad","Influencia",
                "Costo de moderar","Aporte o^2*inf","Moderado","Opinion final"});
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
        TareaConProgreso<Solucion> tarea = alg == 2
                ? avance -> Modex.modexPD(red, avance)
                : avance -> { avance.accept(-1); return correr(alg); };
        trabajarConProgreso(tarea, s -> {
            mostrarSolucion(s);
            texto.setText(resumen(s) + "\nUse 'Verificar costo' para ver el desglose del calculo.\n");
        });
    }

    private void comparar() {
        if (!hayEntrada()) return;
        final boolean conFB = red.n <= Modex.MAX_FB && (red.n <= 24 || confirmarFB());
        trabajarConProgreso(avance -> {
            Comparacion c = new Comparacion();
            if (conFB) c.sols.add(Modex.modexFB(red));
            else if (red.n > Modex.MAX_FB) c.notas.add("Fuerza bruta omitida por tamaño: n = "
                    + red.n + " supera el límite " + Modex.MAX_FB + ".");
            else c.notas.add("Fuerza bruta no se ejecutó porque se canceló la confirmación.");
            c.sols.add(Modex.modexV(red));
            try {
                c.sols.add(Modex.modexPD(red, avance));
            } catch (IllegalArgumentException | OutOfMemoryError ex) {
                c.notas.add("Programación dinámica omitida por tamaño o memoria: " + ex.getMessage());
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
        final RedSocial redActual=red; final Solucion solActual=sol;
        trabajar(() -> crearReporteVerificacion(redActual,solActual),texto::setText);
    }

    private String crearReporteVerificacion(RedSocial red,Solucion sol) {
        long total=0;StringBuilder sb=new StringBuilder(resumen(sol));
        sb.append("\n--- Verificación del esfuerzo: suma de ceil(|o| * (1 - r) * inf) de los moderados ---\n");
        for(int i=0;i<red.n;i++)if(sol.e[i]==1){
            double raw=RedSocial.costoReal(red.op[i],red.rec[i],red.inf[i]);
            sb.append(String.format(Locale.US,"agente %d: ceil(|%s| * (1 - %s) * %s) = ceil(%.4f) = %d%n",i,
                    fmt(red.op[i]),fmt(red.rec[i]),fmt(red.inf[i]),raw,red.costo[i]));
            total+=red.costo[i];
        }
        sb.append(String.format(Locale.US,"Esfuerzo recalculado = %d   %s   Rmax = %d  -> %s%n",total,
                total<=red.rmax?"<=":">",red.rmax,total<=red.rmax?"APLICABLE":"NO APLICABLE"));
        double suma=red.sumaNoModerada(sol.e);
        sb.append("\n--- Verificación del extremismo: Ext = raíz( suma o'^2 * inf ) / n ---\n");
        sb.append(String.format(Locale.US,"Suma de o^2*inf de los NO moderados = %.4f%n",suma));
        sb.append(String.format(Locale.US,"Ext = sqrt(%.4f) / %d = %.6f%n",suma,red.n,red.n==0?0:Math.sqrt(suma)/red.n));
        return sb.toString();
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
        Path salida=fc.getSelectedFile().toPath();Solucion solucion=sol;
        trabajar(() -> {ArchivoIO.escribirSalida(salida,solucion);return salida;},
                archivo -> texto.append("\nSalida guardada en "+archivo.toAbsolutePath()+"\n"));
    }

    private void cargarSalida() {
        if (!hayEntrada()) return;
        JFileChooser fc = new JFileChooser(dirActual);
        fc.setDialogTitle("Seleccione un archivo de salida para verificar");
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        dirActual = fc.getCurrentDirectory();
        Path archivoSalida=fc.getSelectedFile().toPath();int cantidad=red.n;
        trabajar(() -> ArchivoIO.leerSalida(archivoSalida,cantidad),o -> {
            Solucion s = Solucion.crear("Salida cargada (" + fc.getSelectedFile().getName() + ")", red, o.e, 0);
            mostrarSolucion(s);
            StringBuilder sb = new StringBuilder(resumen(s));
            sb.append(String.format(Locale.US, "%nDeclarado en el archivo: Ext = %.6f, Esf = %d%n", o.ext, o.esf));
            sb.append(Math.abs(o.ext - s.extremismo) < 1e-4 && o.esf == s.esfuerzo
                    ? "Los valores declarados COINCIDEN con los recalculados.\n"
                    : "ATENCION: los valores declarados NO coinciden con los recalculados.\n");
            texto.setText(sb.toString());
        });
    }

    /** Genera archivos comparables para todos los casos de una carpeta. */
    private void generarResultados() {
        Path carpetaInicial = GeneradorResultados.buscarCarpetaPruebas();
        JFileChooser selectorPruebas = new JFileChooser(
                carpetaInicial == null ? dirActual : carpetaInicial.toFile());
        selectorPruebas.setDialogTitle("Seleccione la carpeta con los archivos de prueba");
        selectorPruebas.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (selectorPruebas.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final Path carpetaPruebas = selectorPruebas.getSelectedFile().toPath();

        JFileChooser selectorDestino = new JFileChooser(dirActual);
        selectorDestino.setDialogTitle("Seleccione dónde crear la carpeta de resultados");
        selectorDestino.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (selectorDestino.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final Path carpetaDestino = selectorDestino.getSelectedFile().toPath();

        trabajar(() -> GeneradorResultados.generar(carpetaPruebas, carpetaDestino), informe -> {
            dirActual = carpetaDestino.toFile();
            texto.setText(informe.resumen()
                    + "\n\nLa carpeta incluye resumen.csv, las entradas utilizadas y las salidas calculadas.");
            JOptionPane.showMessageDialog(this, informe.resumen(),
                    "Resultados generados", JOptionPane.INFORMATION_MESSAGE);
        });
    }
}
