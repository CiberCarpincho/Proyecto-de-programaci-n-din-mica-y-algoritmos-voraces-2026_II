package modex;

import java.util.Random;

/** Pruebas automaticas: java -jar ModEx.jar --pruebas */
public final class Pruebas {
    private Pruebas() {}

    private static int fallos = 0;

    private static void check(boolean cond, String msg) {
        if (!cond) {
            fallos++;
            System.out.println("  [FALLO] " + msg);
        }
    }

    private static boolean cerca(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    public static boolean ejecutar() {
        System.out.println("== Ejemplos del enunciado ==");
        RedSocial rs1 = new RedSocial(new double[]{100, 100, -10}, new double[]{0.5, 0.1, 0.1},
                new double[]{1, 1, 1}, 55);
        check(rs1.esfuerzo(new int[]{0, 0, 1}) == 9, "Esfuerzo(RS1,<0,0,1>) debe ser 9");
        check(Math.abs(rs1.extremismo(new int[]{0, 0, 1}) - 47.14) < 0.01, "Ext(RS1,<0,0,1>) debe ser 47.14");
        check(rs1.esfuerzo(new int[]{1, 0, 0}) == 50, "Esfuerzo(RS1,<1,0,0>) debe ser 50");
        check(Math.abs(rs1.extremismo(new int[]{1, 0, 0}) - 33.5) < 0.01, "Ext(RS1,<1,0,0>) debe ser 33.5");
        for (Solucion s : new Solucion[]{Modex.modexFB(rs1), Modex.modexV(rs1), Modex.modexPD(rs1)}) {
            check(s.e[0] == 1 && s.e[1] == 0 && s.e[2] == 0 && s.esfuerzo == 50, s.algoritmo + " en RS1");
            System.out.printf("  RS1 %-22s E=%s Esf=%d Ext=%.4f%n", s.algoritmo, java.util.Arrays.toString(s.e),
                    s.esfuerzo, s.extremismo);
        }

        RedSocial rs2 = new RedSocial(new double[]{-30, 20, 50}, new double[]{0.9, 0.1, 0.5},
                new double[]{1, 2, 1}, 35);
        check(rs2.esfuerzo(new int[]{0, 1, 0}) == 36 && !rs2.aplicable(new int[]{0, 1, 0}),
                "E=<0,1,0> en RS2 cuesta 36 y no es aplicable");
        for (Solucion s : new Solucion[]{Modex.modexFB(rs2), Modex.modexV(rs2), Modex.modexPD(rs2)}) {
            check(s.e[0] == 1 && s.e[1] == 0 && s.e[2] == 1 && s.esfuerzo == 28
                    && Math.abs(s.extremismo - 9.43) < 0.01, s.algoritmo + " en RS2");
            System.out.printf("  RS2 %-22s E=%s Esf=%d Ext=%.4f%n", s.algoritmo, java.util.Arrays.toString(s.e),
                    s.esfuerzo, s.extremismo);
        }

        System.out.println("== Aleatorias: FB vs PD (deben coincidir) y voraz (nunca mejor que el optimo) ==");
        int casos = 400, vorazOptimo = 0;
        double peorGap = 0;
        Random r = new Random(2026);
        for (int t = 0; t < casos; t++) {
            int n = 1 + r.nextInt(14);
            double frac = 0.05 + r.nextDouble() * 0.95;
            RedSocial rs = Generador.generar(n, r.nextLong(), frac);
            Solucion fb = Modex.modexFB(rs), pd = Modex.modexPD(rs), v = Modex.modexV(rs);
            check(rs.aplicable(fb.e) && rs.aplicable(pd.e) && rs.aplicable(v.e), "aplicabilidad caso " + t);
            check(cerca(fb.extremismo, pd.extremismo), "FB y PD difieren en caso " + t);
            check(v.extremismo >= fb.extremismo - 1e-9, "voraz mejor que el optimo en caso " + t);
            if (cerca(v.extremismo, fb.extremismo)) vorazOptimo++;
            else if (fb.extremismo > 0) peorGap = Math.max(peorGap, (v.extremismo - fb.extremismo) / fb.extremismo);
        }
        System.out.printf("  Voraz hallo el optimo en %d de %d casos (peor brecha en Ext: %.2f%%)%n",
                vorazOptimo, casos, peorGap * 100);

        System.out.println(fallos == 0 ? "TODAS LAS PRUEBAS PASARON" : fallos + " PRUEBA(S) FALLARON");
        return fallos == 0;
    }
}
