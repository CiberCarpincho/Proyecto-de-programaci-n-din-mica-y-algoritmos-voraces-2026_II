package modex;

import java.util.Random;

/** Crea redes aleatorias que se pueden volver a generar usando la misma semilla. */
public final class Generador {
    private Generador() {}

    /**
     * @param n cantidad de agentes
     * @param semilla valor que hace reproducible el caso aleatorio
     * @param fraccion parte del costo total que se asigna como presupuesto, entre 0 y 1
     */
    public static RedSocial generar(int n, long semilla, double fraccion) {
        Random aleatorio = new Random(semilla);
        double[] opiniones = new double[n];
        double[] receptividades = new double[n];
        double[] influencias = new double[n];
        long costoTotal = 0;

        for (int agente = 0; agente < n; agente++) {
            opiniones[agente] = aleatorio.nextInt(201) - 100;
            receptividades[agente] = Math.round(aleatorio.nextDouble() * 100) / 100.0;
            influencias[agente] = 1.0 + Math.round(aleatorio.nextDouble() * 20) / 10.0;
            costoTotal += RedSocial.costoAgente(opiniones[agente],
                    receptividades[agente], influencias[agente]);
        }

        int presupuesto = (int) Math.min(Integer.MAX_VALUE,
                Math.ceil(fraccion * costoTotal));
        return new RedSocial(opiniones, receptividades, influencias, presupuesto);
    }
}
