package modex;

/**
 * Red social RS = <AG, Rmax>. Guarda los agentes (opinion, receptividad, influencia)
 * y precalcula, para cada agente:
 *   costo[i]  = ceil(|o_i| * (1 - r_i) * inf_i)   (esfuerzo de moderarlo)
 *   aporte[i] = o_i^2 * inf_i                     (lo que "suma" a la raiz de Ext si NO se modera)
 */
public final class RedSocial {
    /** Tolerancia para evitar que errores de punto flotante (ej. 9.000000000000002) alteren el techo. */
    private static final double TOL = 1e-9;

    public final int n;
    public final double[] op, rec, inf;
    public final int rmax;
    public final int[] costo;
    public final double[] aporte;

    public RedSocial(double[] op, double[] rec, double[] inf, int rmax) {
        this.n = op.length;
        this.op = op;
        this.rec = rec;
        this.inf = inf;
        this.rmax = rmax;
        this.costo = new int[n];
        this.aporte = new double[n];
        for (int i = 0; i < n; i++) {
            costo[i] = costoAgente(op[i], rec[i], inf[i]);
            aporte[i] = op[i] * op[i] * inf[i];
        }
    }

    /** Valor real (antes del techo) del esfuerzo de moderar a un agente. */
    public static double costoReal(double o, double r, double inf) {
        return Math.abs(o) * (1.0 - r) * inf;
    }

    public static int costoAgente(double o, double r, double inf) {
        return (int) Math.ceil(costoReal(o, r, inf) - TOL);
    }

    /** Esfuerzo(RS, E) = suma de los costos de los agentes moderados. */
    public long esfuerzo(int[] e) {
        long s = 0;
        for (int i = 0; i < n; i++) if (e[i] == 1) s += costo[i];
        return s;
    }

    /** Suma de o_i^2 * inf_i de los agentes que NO se moderan (lo que queda dentro de la raiz). */
    public double sumaNoModerada(int[] e) {
        double s = 0;
        for (int i = 0; i < n; i++) if (e[i] == 0) s += aporte[i];
        return s;
    }

    /** Ext(Mod(RS,E)) = raiz( sum (o'_i^2 * inf_i) ) / n  (formula del enunciado; verificada con sus ejemplos). */
    public double extremismo(int[] e) {
        if (n == 0) return 0;
        return Math.sqrt(sumaNoModerada(e)) / n;
    }

    public double extremismoOriginal() {
        return extremismo(new int[n]);
    }

    public boolean aplicable(int[] e) {
        return esfuerzo(e) <= rmax;
    }
}
