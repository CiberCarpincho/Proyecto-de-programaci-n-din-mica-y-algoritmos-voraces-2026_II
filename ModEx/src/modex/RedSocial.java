package modex;

/**
 * Datos de la red social y operaciones para evaluar una estrategia.
 *
 * Una estrategia usa 1 para moderar a un agente y 0 para dejar su opinión igual.
 */
public final class RedSocial {
    /** Corrige pequeños errores de redondeo antes de calcular el techo del costo. */
    private static final double TOLERANCIA_TECHO = 1e-9;

    public final int n;
    public final double[] op;
    public final double[] rec;
    public final double[] inf;
    public final int rmax;
    public final int[] costo;
    public final double[] aporte;

    public RedSocial(double[] opiniones, double[] receptividades,
            double[] influencias, int presupuestoMaximo) {
        n = opiniones.length;
        op = opiniones;
        rec = receptividades;
        inf = influencias;
        rmax = presupuestoMaximo;

        costo = new int[n];
        aporte = new double[n];
        for (int agente = 0; agente < n; agente++) {
            costo[agente] = costoAgente(op[agente], rec[agente], inf[agente]);
            aporte[agente] = op[agente] * op[agente] * inf[agente];
        }
    }

    /** Calcula el costo real de moderar una opinión antes de redondearlo hacia arriba. */
    public static double costoReal(double opinion, double receptividad, double influencia) {
        return Math.abs(opinion) * (1.0 - receptividad) * influencia;
    }

    /** Calcula el costo entero de moderar un agente, según el techo del enunciado. */
    public static int costoAgente(double opinion, double receptividad, double influencia) {
        double costoSinRedondear = costoReal(opinion, receptividad, influencia);
        return (int) Math.ceil(costoSinRedondear - TOLERANCIA_TECHO);
    }

    /** Suma los costos de todos los agentes marcados como moderados. */
    public long esfuerzo(int[] estrategia) {
        long esfuerzoTotal = 0;
        for (int agente = 0; agente < n; agente++) {
            if (estrategia[agente] == 1) {
                esfuerzoTotal += costo[agente];
            }
        }
        return esfuerzoTotal;
    }

    /** Suma el aporte al extremismo de los agentes que no fueron moderados. */
    public double sumaNoModerada(int[] estrategia) {
        double sumaAportes = 0;
        for (int agente = 0; agente < n; agente++) {
            if (estrategia[agente] == 0) {
                sumaAportes += aporte[agente];
            }
        }
        return sumaAportes;
    }

    /** Calcula Ext = raíz de la suma no moderada dividida por la cantidad de agentes. */
    public double extremismo(int[] estrategia) {
        if (n == 0) {
            return 0;
        }
        return Math.sqrt(sumaNoModerada(estrategia)) / n;
    }

    /** Calcula el extremismo antes de moderar a ningún agente. */
    public double extremismoOriginal() {
        return extremismo(new int[n]);
    }

    /** Indica si la estrategia respeta el presupuesto disponible. */
    public boolean aplicable(int[] estrategia) {
        return esfuerzo(estrategia) <= rmax;
    }
}
