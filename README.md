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

## Generar una carpeta de resultados

En la interfaz, pulsa **Generar carpeta de resultados...**, selecciona la carpeta con los casos `.txt` y elige dónde guardar el resultado. La aplicación crea una carpeta `Resultados_ModEx_fecha_hora` con:

- `resumen.csv`: una fila por caso y algoritmo, con estado, extremismo, esfuerzo, presupuesto, tiempo y diferencia porcentual frente a la mejor solución calculada para ese caso.
- `entradas/`: copias de los archivos usados. Si junto a la batería existe una carpeta `ejemplos`, también se incluyen sus casos.
- `salidas/`: archivos de salida ModEx para los algoritmos que se ejecutaron.
- `LEEME.txt`: explicación de los archivos y lista de ejecuciones omitidas.

El informe ejecuta voraz en todos los casos. Para mantener razonable el tiempo total, omite fuerza bruta cuando hay más de 20 agentes; omite programación dinámica cuando la tabla excede el límite de 200 millones de celdas. Estas omisiones y sus motivos aparecen en `resumen.csv` y `LEEME.txt`.

La brecha porcentual se calcula frente al mejor resultado entre los algoritmos que sí se ejecutaron. Si se omitieron los algoritmos exactos, esa cifra no representa la diferencia frente al óptimo global.

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
| `GeneradorResultados.java` | Procesa carpetas de casos y crea el resumen y las salidas para el informe. |
| `ModexGUI.java` | Interfaz Swing para cargar, generar, ejecutar, comparar y verificar casos, y crear informes por lotes. |
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
