package modex;

import java.util.Arrays;

/**
 * Implementa las tres estrategias para resolver ModEx.
 *
 * Moderar al agente i elimina su aporte o_i^2 * inf_i del extremismo y cuesta
 * costo[i]. Por eso, elegir agentes equivale a una mochila 0/1: maximizar el
 * aporte eliminado sin superar Rmax.
 */
public final class Modex {
    private Modex() {}

    private static final double EPSILON = 1e-9;

    /** Fuerza bruta examina 2^n estrategias; se limita para evitar tiempos enormes. */
    public static final int MAX_FB = 30;

    /** Límite de memoria para la tabla de decisiones de programación dinámica. */
    public static final long MAX_CELDAS_PD = 200_000_000L;

    // ---------------------------------------------------------------------
    // Fuerza bruta

    /** Prueba cada combinación de agentes moderados y conserva la mejor aplicable. */
    public static Solucion modexFB(RedSocial red) {
        if (red.n > MAX_FB) {
            throw new IllegalArgumentException(
                    "Fuerza bruta limitada a n <= " + MAX_FB + " (n actual = " + red.n + ").");
        }

        long inicio = System.nanoTime();
        long mejorEstrategia = 0;
        long mejorEsfuerzo = 0;
        double mejorAporteEliminado = -1;
        long cantidadEstrategias = 1L << red.n;

        for (long estrategia = 0; estrategia < cantidadEstrategias; estrategia++) {
            long esfuerzo = 0;
            double aporteEliminado = 0;
            boolean aplicable = true;

            for (int agente = 0; agente < red.n; agente++) {
                if (!estaModerado(estrategia, agente)) {
                    continue;
                }

                esfuerzo += red.costo[agente];
                if (esfuerzo > red.rmax) {
                    aplicable = false;
                    break;
                }
                aporteEliminado += red.aporte[agente];
            }

            if (aplicable && esMejor(aporteEliminado, esfuerzo,
                    mejorAporteEliminado, mejorEsfuerzo)) {
                mejorEstrategia = estrategia;
                mejorEsfuerzo = esfuerzo;
                mejorAporteEliminado = aporteEliminado;
            }
        }

        int[] agentesModerados = new int[red.n];
        for (int agente = 0; agente < red.n; agente++) {
            agentesModerados[agente] = estaModerado(mejorEstrategia, agente) ? 1 : 0;
        }

        return Solucion.crear("Fuerza bruta", red, agentesModerados,
                System.nanoTime() - inicio);
    }

    /** Indica si el bit del agente está encendido en la estrategia examinada. */
    private static boolean estaModerado(long estrategia, int agente) {
        return ((estrategia >> agente) & 1L) == 1L;
    }

    /** Compara soluciones: más aporte eliminado y, en empate, menos esfuerzo. */
    private static boolean esMejor(double aporte, long esfuerzo,
            double mejorAporte, long mejorEsfuerzo) {
        if (aporte > mejorAporte + EPSILON) {
            return true;
        }
        return Math.abs(aporte - mejorAporte) <= EPSILON
                && esfuerzo < mejorEsfuerzo;
    }

    // ---------------------------------------------------------------------
    // Algoritmo voraz

    /**
     * Ordena los agentes por aporte eliminado por unidad de esfuerzo. Luego
     * toma los que quepan y compara el resultado con moderar solo al mejor agente.
     */
    public static Solucion modexV(RedSocial red) {
        long inicio = System.nanoTime();

        Integer[] candidatos = crearCandidatos(red);
        Arrays.sort(candidatos, (agenteA, agenteB) -> compararCandidatos(red, agenteA, agenteB));

        int[] agentesModerados = new int[red.n];
        long esfuerzoRestante = red.rmax;
        double aporteEliminado = 0;

        for (int agente : candidatos) {
            if (red.costo[agente] <= esfuerzoRestante) {
                agentesModerados[agente] = 1;
                esfuerzoRestante -= red.costo[agente];
                aporteEliminado += red.aporte[agente];
            }
        }

        int mejorAgenteIndividual = buscarMejorAgenteIndividual(red, candidatos);
        if (mejorAgenteIndividual >= 0
                && red.aporte[mejorAgenteIndividual] > aporteEliminado + EPSILON) {
            agentesModerados = new int[red.n];
            agentesModerados[mejorAgenteIndividual] = 1;
        }

        return Solucion.crear("Voraz", red, agentesModerados,
                System.nanoTime() - inicio);
    }

    /** Devuelve los agentes útiles que caben por sí solos en el presupuesto. */
    private static Integer[] crearCandidatos(RedSocial red) {
        Integer[] candidatosTemporales = new Integer[red.n];
        int cantidad = 0;

        for (int agente = 0; agente < red.n; agente++) {
            boolean tieneAporte = red.aporte[agente] > 0;
            boolean cabeSolo = red.costo[agente] <= red.rmax;
            if (tieneAporte && cabeSolo) {
                candidatosTemporales[cantidad] = agente;
                cantidad++;
            }
        }

        return Arrays.copyOf(candidatosTemporales, cantidad);
    }

    /** Ordena por mayor razón aporte/costo; resuelve empates por aporte e índice. */
    private static int compararCandidatos(RedSocial red, int agenteA, int agenteB) {
        int ordenPorRazon = Double.compare(razonAporteCosto(red, agenteB),
                razonAporteCosto(red, agenteA));
        if (ordenPorRazon != 0) {
            return ordenPorRazon;
        }

        int ordenPorAporte = Double.compare(red.aporte[agenteB], red.aporte[agenteA]);
        if (ordenPorAporte != 0) {
            return ordenPorAporte;
        }
        return Integer.compare(agenteA, agenteB);
    }

    /** Un agente de costo cero tiene prioridad máxima si su aporte es positivo. */
    private static double razonAporteCosto(RedSocial red, int agente) {
        if (red.costo[agente] == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return red.aporte[agente] / red.costo[agente];
    }

    private static int buscarMejorAgenteIndividual(RedSocial red, Integer[] candidatos) {
        int mejorAgente = -1;
        for (int agente : candidatos) {
            if (mejorAgente < 0 || red.aporte[agente] > red.aporte[mejorAgente]) {
                mejorAgente = agente;
            }
        }
        return mejorAgente;
    }

    // ---------------------------------------------------------------------
    // Programación dinámica

    /**
     * Resuelve la mochila 0/1 con una fila de valores y una tabla de decisiones.
     *
     * mejorAporte[presupuesto] guarda el máximo aporte que se puede eliminar con
     * los agentes ya procesados. Se recorren los presupuestos de mayor a menor
     * para que cada agente se use como máximo una vez. La tabla de decisiones
     * permite reconstruir qué agentes formaron la solución.
     */
    public static Solucion modexPD(RedSocial red) {
        int cantidadAgentes = red.n;
        int presupuestoMaximo = red.rmax;
        long cantidadCeldas = (long) cantidadAgentes * (presupuestoMaximo + 1L);

        if (cantidadCeldas > MAX_CELDAS_PD) {
            throw new IllegalArgumentException(
                    "Entrada demasiado grande para PD en memoria: n*(Rmax+1) = "
                    + cantidadCeldas + " > " + MAX_CELDAS_PD + ".");
        }

        long inicio = System.nanoTime();
        boolean[][] seModeraEnMejorSolucion =
                new boolean[cantidadAgentes][presupuestoMaximo + 1];
        double[] mejorAporte = new double[presupuestoMaximo + 1];

        for (int agente = 0; agente < cantidadAgentes; agente++) {
            int costo = red.costo[agente];
            double aporte = red.aporte[agente];

            if (aporte <= 0) {
                continue;
            }

            for (int presupuesto = presupuestoMaximo; presupuesto >= costo; presupuesto--) {
                double aporteSiSeModera = mejorAporte[presupuesto - costo] + aporte;
                if (aporteSiSeModera > mejorAporte[presupuesto] + EPSILON) {
                    mejorAporte[presupuesto] = aporteSiSeModera;
                    seModeraEnMejorSolucion[agente][presupuesto] = true;
                }
            }
        }

        int[] agentesModerados = reconstruirSolucion(red, seModeraEnMejorSolucion);
        return Solucion.crear("Programacion dinamica", red, agentesModerados,
                System.nanoTime() - inicio);
    }

    /** Sigue las decisiones desde el último agente hasta reconstruir la estrategia. */
    private static int[] reconstruirSolucion(RedSocial red, boolean[][] decisiones) {
        int[] agentesModerados = new int[red.n];
        int presupuestoRestante = red.rmax;

        for (int agente = red.n - 1; agente >= 0; agente--) {
            if (decisiones[agente][presupuestoRestante]) {
                agentesModerados[agente] = 1;
                presupuestoRestante -= red.costo[agente];
            }
        }

        return agentesModerados;
    }
}
