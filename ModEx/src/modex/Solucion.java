package modex;

/** Resultado de un algoritmo: la tripleta (E, Esfuerzo(RS,E), Ext(Mod(RS,E))) mas nombre y tiempo. */
public final class Solucion {
    public final String algoritmo;
    public final int[] e;
    public final long esfuerzo;
    public final double extremismo;
    public final long nanos;

    public Solucion(String algoritmo, int[] e, long esfuerzo, double extremismo, long nanos) {
        this.algoritmo = algoritmo;
        this.e = e;
        this.esfuerzo = esfuerzo;
        this.extremismo = extremismo;
        this.nanos = nanos;
    }

    public static Solucion crear(String algoritmo, RedSocial rs, int[] e, long nanos) {
        return new Solucion(algoritmo, e, rs.esfuerzo(e), rs.extremismo(e), nanos);
    }

    public double milisegundos() {
        return nanos / 1e6;
    }
}
