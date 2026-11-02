package modex;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lectura/escritura de los formatos de entrada y salida del enunciado. */
public final class ArchivoIO {
    private ArchivoIO() {}

    /** Contenido de una salida leida desde archivo. */
    public static final class Salida {
        public double ext;
        public long esf;
        public int[] e;
    }

    private static List<String> lineasUtiles(Path p) throws IOException {
        List<String> res = new ArrayList<>();
        for (String l : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            String s = l.replace("\uFEFF", "").trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            res.add(s);
        }
        return res;
    }

    private static double num(String s, String que, int linea) throws IOException {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException ex) {
            throw new IOException("Linea " + linea + ": " + que + " no es un numero valido: '" + s + "'");
        }
    }

    /** Formato: n / n lineas "opinion,receptividad,influencia" / Rmax. */
    public static RedSocial leerEntrada(Path p) throws IOException {
        List<String> ls = lineasUtiles(p);
        if (ls.size() < 2) throw new IOException("Archivo incompleto.");
        int n;
        try {
            n = Integer.parseInt(ls.get(0));
        } catch (NumberFormatException ex) {
            throw new IOException("La primera linea debe ser un entero n.");
        }
        if (n < 0) throw new IOException("n no puede ser negativo.");
        if (ls.size() < n + 2)
            throw new IOException("Se esperaban " + (n + 2) + " lineas y el archivo tiene " + ls.size() + ".");
        double[] op = new double[n], rec = new double[n], inf = new double[n];
        for (int i = 0; i < n; i++) {
            String[] t = ls.get(1 + i).split("[,;\\s]+");
            if (t.length != 3)
                throw new IOException("Agente " + i + ": se esperaban 3 valores (opinion, receptividad, influencia).");
            op[i] = num(t[0], "la opinion", i + 2);
            rec[i] = num(t[1], "la receptividad", i + 2);
            inf[i] = num(t[2], "la influencia", i + 2);
            if (op[i] < -100 || op[i] > 100) throw new IOException("Agente " + i + ": opinion fuera de [-100,100].");
            if (rec[i] < 0 || rec[i] > 1) throw new IOException("Agente " + i + ": receptividad fuera de [0,1].");
            if (inf[i] < 1 || inf[i] > 3) throw new IOException("Agente " + i + ": influencia fuera de [1,3].");
        }
        double r = num(ls.get(n + 1), "Rmax", n + 2);
        if (r < 0 || r != Math.rint(r) || r > Integer.MAX_VALUE)
            throw new IOException("Rmax debe ser un entero no negativo.");
        return new RedSocial(op, rec, inf, (int) r);
    }

    private static String f(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e9) return String.valueOf((long) d);
        return String.format(Locale.US, "%s", Double.toString(d));
    }

    public static void escribirEntrada(Path p, RedSocial rs) throws IOException {
        List<String> ls = new ArrayList<>();
        ls.add(String.valueOf(rs.n));
        for (int i = 0; i < rs.n; i++) ls.add(f(rs.op[i]) + "," + f(rs.rec[i]) + "," + f(rs.inf[i]));
        ls.add(String.valueOf(rs.rmax));
        Files.write(p, ls, StandardCharsets.UTF_8);
    }

    /** Formato: Ext / Esf / n lineas con 0 o 1. */
    public static void escribirSalida(Path p, Solucion s) throws IOException {
        List<String> ls = new ArrayList<>();
        ls.add(String.format(Locale.US, "%.6f", s.extremismo));
        ls.add(String.valueOf(s.esfuerzo));
        for (int v : s.e) ls.add(String.valueOf(v));
        Files.write(p, ls, StandardCharsets.UTF_8);
    }

    public static Salida leerSalida(Path p, int n) throws IOException {
        List<String> ls = lineasUtiles(p);
        if (ls.size() < n + 2)
            throw new IOException("La salida debe tener " + (n + 2) + " lineas y tiene " + ls.size() + ".");
        Salida s = new Salida();
        s.ext = num(ls.get(0), "Ext", 1);
        s.esf = (long) num(ls.get(1), "Esf", 2);
        s.e = new int[n];
        for (int i = 0; i < n; i++) {
            String t = ls.get(2 + i);
            if (!t.equals("0") && !t.equals("1"))
                throw new IOException("Linea " + (3 + i) + ": se esperaba 0 o 1 y se encontro '" + t + "'.");
            s.e[i] = Integer.parseInt(t);
        }
        return s;
    }
}
