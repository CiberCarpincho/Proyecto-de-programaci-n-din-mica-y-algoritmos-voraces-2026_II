package modex;

import java.util.Random;

/** Generador de casos de prueba aleatorios (reproducibles con la semilla). */
public final class Generador {
    private Generador() {}

    /**
     * @param n    numero de agentes
     * @param seed semilla
     * @param frac fraccion (0,1] del esfuerzo total necesario para moderar a TODOS, que se usa como Rmax
     */
    public static RedSocial generar(int n, long seed, double frac) {
        Random r = new Random(seed);
        double[] op = new double[n], rec = new double[n], inf = new double[n];
        long total = 0;
        for (int i = 0; i < n; i++) {
            op[i] = r.nextInt(201) - 100;                         // entero en [-100,100]
            rec[i] = Math.round(r.nextDouble() * 100) / 100.0;    // [0,1] con 2 decimales
            inf[i] = 1.0 + Math.round(r.nextDouble() * 20) / 10.0; // [1,3] con 1 decimal
            total += RedSocial.costoAgente(op[i], rec[i], inf[i]);
        }
        int rmax = (int) Math.min(Integer.MAX_VALUE, Math.ceil(frac * total));
        return new RedSocial(op, rec, inf, rmax);
    }
}
