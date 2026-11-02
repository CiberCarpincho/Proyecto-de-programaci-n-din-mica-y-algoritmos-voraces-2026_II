# ModEx

Proyecto de Análisis y Diseño de Algoritmos II: selecciona qué agentes de una red social moderar para reducir su extremismo sin superar un presupuesto de esfuerzo.

## Requisitos

- Java 11 o superior.
- Para compilar desde el código fuente, se necesita un JDK con `javac` y `jar`.

## Compilar y ejecutar

Desde la carpeta `ModEx`:

**Windows (PowerShell o CMD):**

```bat
compilar.bat
java -jar ModEx.jar
```

**macOS o Linux:**

```sh
sh compilar.sh
java -jar ModEx.jar
```

Sin argumentos, el programa abre la interfaz gráfica. Para procesar un archivo desde la consola:

```sh
java -jar ModEx.jar entrada.txt salida.txt PD
```

El último argumento selecciona el algoritmo: `FB` (fuerza bruta), `V` (voraz) o `PD` (programación dinámica). Si se omite, se usa `PD`.

Para ejecutar las pruebas automáticas incluidas:

```sh
java -jar ModEx.jar --pruebas
```

## Cómo funciona

Cada agente tiene una opinión, una receptividad y una influencia. Moderarlo cuesta `ceil(|opinión| × (1 − receptividad) × influencia)`. La estrategia es aplicable si la suma de esos costos no supera `Rmax`.

El extremismo se calcula como `raíz(suma de opinión² × influencia de los agentes no moderados) / n`. Como moderar un agente elimina su aporte al extremismo, el problema se puede tratar como una mochila 0/1: elegir agentes cuyo aporte eliminado sea máximo sin exceder el presupuesto.

El programa implementa tres estrategias:

- **Fuerza bruta (`modexFB`)**: revisa todas las combinaciones posibles y encuentra el óptimo. Su tiempo crece exponencialmente; la implementación limita este algoritmo a 30 agentes.
- **Voraz (`modexV`)**: ordena los agentes por aporte eliminado dividido por costo, y selecciona los que caben. Es rápido, pero no garantiza el óptimo.
- **Programación dinámica (`modexPD`)**: calcula una solución óptima usando subproblemas de mochila y reconstruye qué agentes moderar. Su tiempo y espacio dependen de `n × Rmax`; el programa limita la tabla a 200 millones de celdas.

La salida de cada algoritmo incluye la estrategia, su esfuerzo y el extremismo resultante.

## Organización del código

El código fuente está en `ModEx/src/modex/`:

| Archivo | Responsabilidad |
| --- | --- |
| `Main.java` | Inicia la interfaz gráfica o procesa argumentos de consola. |
| `Modex.java` | Implementa fuerza bruta, voraz y programación dinámica. |
| `RedSocial.java` | Guarda los datos de la red y calcula costos, esfuerzo y extremismo. |
| `Solucion.java` | Representa una estrategia y sus resultados. |
| `ArchivoIO.java` | Lee entradas y salidas, y escribe archivos de resultado. |
| `Generador.java` | Crea redes aleatorias reproducibles mediante una semilla. |
| `ModexGUI.java` | Interfaz Swing para cargar, generar, ejecutar, comparar y verificar casos. |
| `Pruebas.java` | Prueba los ejemplos del enunciado y compara los algoritmos en casos aleatorios pequeños. |

`ModEx/ejemplos/` contiene entradas de ejemplo y `ModEx/Bateria de pruebas/` contiene los casos de la batería y su tabla de resultados. El PDF del enunciado y las imágenes de criterios están en la raíz del repositorio.

## Formatos de archivo

Una entrada tiene `n + 2` líneas: cantidad de agentes, una línea por agente y presupuesto máximo. Cada agente se escribe como `opinión,receptividad,influencia`.

```text
3
100,0.5,1
100,0.1,1
-10,0.1,1
55
```

Una salida contiene el extremismo, el esfuerzo y luego un `0` o `1` por agente (`1` indica moderado). Se ignoran líneas vacías y comentarios que comiencen con `#`.
