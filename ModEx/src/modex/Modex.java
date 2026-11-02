package modex;

import java.util.Arrays;

/**
 * Las tres estrategias de diseno para ModEx.
 * Reformulacion usada por las tres: minimizar Ext equivale a MAXIMIZAR la suma de
 * aporte[i] = o_i^2 * inf_i de los agentes moderados, sujeto a que la suma de costo[i]
 * de los moderados sea <= Rmax (un problema tipo mochila 0/1).
 */
public final class Modex {
    private Modex() {}

    private static final double EPS = 1e-9;
    /** Fuerza bruta: 2^n subconjuntos; se limita n para no colgar la maquina. */
    public static final int MAX_FB = 30;
    /** Programacion dinamica: maximo de celdas (n * (Rmax+1)) de la tabla de decisiones. */
    public static final long MAX_CELDAS_PD = 200_000_000L;

    // ------------------------------------------------------------------ FUERZA BRUTA
    /** Genera los 2^n subconjuntos, descarta los no aplicables y se queda con el de menor extremismo. */
    public static Solucion modexFB(RedSocial rs) {
        final int n = rs.n;
        if (n > MAX_FB)
            throw new IllegalArgumentException("Fuerza bruta limitada a n <= " + MAX_FB + " (n actual = " + n + ").");
        long t0 = System.nanoTime();
        long mejorMask = 0;
        double mejorAporte = -1;
        long mejorEsf = 0;
        long total = 1L << n;
        for (long mask = 0; mask < total; mask++) {
            long esf = 0;
            double ap = 0;
            boolean ok = true;
            for (int i = 0; i < n; i++) {
                if (((mask >> i) & 1L) != 0) {
                    esf += rs.costo[i];
                    if (esf > rs.rmax) { ok = false; break; }
                    ap += rs.aporte[i];
                }
            }
            if (!ok) continue;
            // mas aporte moderado = menos extremismo; a igualdad, menos esfuerzo
            if (ap > mejorAporte + EPS || (Math.abs(ap - mejorAporte) <= EPS && esf < mejorEsf)) {
                mejorAporte = ap;
                mejorEsf = esf;
                mejorMask = mask;
            }
        }
        int[] e = new int[n];
        for (int i = 0; i < n; i++) e[i] = (int) ((mejorMask >> i) & 1L);
        return Solucion.crear("Fuerza bruta", rs, e, System.nanoTime() - t0);
    }

    // ------------------------------------------------------------------ VORAZ
    private static double razon(RedSocial rs, int i) {
        return rs.costo[i] == 0 ? Double.POSITIVE_INFINITY : rs.aporte[i] / rs.costo[i];
    }

    /**
     * Voraz: criterio de seleccion = mayor razon (reduccion de extremismo / esfuerzo).
     *  1) Ordena los agentes utiles por razon aporte/costo descendente (los de costo 0 van primero).
     *  2) Recorre en ese orden y modera a cada agente al que aun le alcance el presupuesto
     *     (si uno no cabe, lo salta y sigue con los demas).
     *  3) Compara contra la mejor moderacion de UN SOLO agente y se queda con la mejor
     *     (evita el peor caso clasico de la mochila en que un agente muy grande queda por fuera).
     * Complejidad: O(n log n).
     */
    public static Solucion modexV(RedSocial rs) {
        long t0 = System.nanoTime();
        final int n = rs.n;
        int cnt = 0;
        Integer[] idx = new Integer[n];
        for (int i = 0; i < n; i++)
            if (rs.aporte[i] > 0 && rs.costo[i] <= rs.rmax) idx[cnt++] = i;
        Integer[] cand = Arrays.copyOf(idx, cnt);
        Arrays.sort(cand, (a, b) -> {
            int c = Double.compare(razon(rs, b), razon(rs, a));
            if (c != 0) return c;
            c = Double.compare(rs.aporte[b], rs.aporte[a]);
            return c != 0 ? c : Integer.compare(a, b);
        });

        int[] e = new int[n];
        long restante = rs.rmax;
        double aporteVoraz = 0;
        for (int k : cand) {
            if (rs.costo[k] <= restante) {
                e[k] = 1;
                restante -= rs.costo[k];
                aporteVoraz += rs.aporte[k];
            }
        }
        int mejorUno = -1;
        for (int k : cand)
            if (mejorUno < 0 || rs.aporte[k] > rs.aporte[mejorUno]) mejorUno = k;
        if (mejorUno >= 0 && rs.aporte[mejorUno] > aporteVoraz + EPS) {
            e = new int[n];
            e[mejorUno] = 1;
        }
        return Solucion.crear("Voraz", rs, e, System.nanoTime() - t0);
    }

    // ------------------------------------------------------------------ PROGRAMACION DINAMICA
    /**
     * PD (mochila 0/1). Subproblema (i, w): mejor aporte moderable usando solo los agentes 0..i-1
     * con presupuesto w.
     *   V(i, w) = V(i-1, w)                                   si costo[i-1] > w
     *   V(i, w) = max( V(i-1, w), V(i-1, w-c) + aporte )      si no
     * Los valores se llevan en un arreglo de una fila (se recorre w de mayor a menor); las decisiones
     * se guardan en toma[i][w] para reconstruir la estrategia optima.
     * Tiempo O(n * Rmax). Espacio O(n * Rmax) por la tabla de decisiones.
     */
    public static Solucion modexPD(RedSocial rs) {
        final int n = rs.n, R = rs.rmax;
        if ((long) n * (R + 1L) > MAX_CELDAS_PD)
            throw new IllegalArgumentException("Entrada demasiado grande para PD en memoria: n*(Rmax+1) = "
                    + ((long) n * (R + 1L)) + " > " + MAX_CELDAS_PD + ".");
        long t0 = System.nanoTime();
        boolean[][] toma = new boolean[n][R + 1];
        double[] dp = new double[R + 1];
        for (int i = 0; i < n; i++) {
            int c = rs.costo[i];
            double b = rs.aporte[i];
            if (b <= 0) continue;
            for (int w = R; w >= c; w--) {
                double cand = dp[w - c] + b;
                if (cand > dp[w] + EPS) {
                    dp[w] = cand;
                    toma[i][w] = true;
                }
            }
        }
        int[] e = new int[n];
        int w = R;
        for (int i = n - 1; i >= 0; i--) {
            if (toma[i][w]) {
                e[i] = 1;
                w -= rs.costo[i];
            }
        }
        return Solucion.crear("Programacion dinamica", rs, e, System.nanoTime() - t0);
    }
}
