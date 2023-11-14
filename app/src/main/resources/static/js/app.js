$(document).ready(
    function () {
        var qrDir = null
        var dir = null
        $("#shortener").submit(

            // // Obtén el valor de la casilla de verificación generateQR
           // val generateQR = $("#generateQR").is(":checked")

            // // Crea un objeto de datos para la solicitud que incluye generateQR
            // //Esto es lo que mandamos cuando se escribe la url
            // var requestData = {
            //     data: $(this).serialize(),
            //     generateQR: generateQR
            // };
            
            function (event) {
                event.preventDefault();

                /**
                 * Verificar si se ha seleccionado un archivo CSV
                 */
                var fileInput = document.getElementById('file');
                var hayCsv = fileInput && fileInput.files.length > 0;

                /**
                 * Comprobar si está marcado el check de generar QR
                 */

                // Obtén el valor de la casilla de verificación generateQR
                var generateQR = $("#qr").is(":checked")

                // Crea un objeto de datos para la solicitud que incluye generateQR
                //Esto es lo que mandamos cuando se escribe la url
                // Obtén la cadena de consulta serializada
                var serializedData = $(this).serialize();

                // Agrega el nuevo dato a la cadena de consulta
                serializedData += "&generateQr=" + generateQR;
                console.log(serializedData)

                if (!hayCsv) {
                    $.ajax({
                        type: "POST",
                        url: "/api/link",
                        data: serializedData,
                        success: function (msg, status, request) {
                            qrDir = msg.qr
                            dir = msg.url
                            if (generateQR) {
                                $("#showQr").show();
                            } else {
                                $("#showQr").hide();
                            }
                            $("#downloadCSV").hide();
                            $("#result").html(
                                "<div class='alert alert-success lead'><a target='_blank' href='"
                                + request.getResponseHeader('Location')
                                + "'>"
                                + request.getResponseHeader('Location')
                                + "</a></div>");
                           // Acceder a la propiedad "sumary" dentro del objeto "properties"
                    
                        
                        },
                        error: function () {
                            $("#result").html(
                                "<div class='alert alert-danger lead'>ERROR</div>");
                        }
                    });
                } else {
                    /**
                     * Si hay un archivo CSV, entonces se envía a /api/bulk
                     */
                    var formData = new FormData($(this)[0]);
                    $.ajax({
                        type: "POST",
                        url: "/api/bulk",
                        data: formData,
                        contentType: false,
                        processData: false,
                        success: function (msg, status, request) {
                            $("#downloadCSV").hide();
                            if (request.status === 200) {
                                $("#result").html(
                                    "<div class='alert alert-warning lead'>File is empty</div>");
                                return;
                            }
                            var isCSV = request.getResponseHeader('Content-Type').includes("text/csv");
                            if (isCSV) {
                                // Crear un enlace para descargar el archivo CSV
                                var blob = new Blob([msg], { type: "text/csv" });
                                var csvURL = URL.createObjectURL(blob);
                                var a = document.createElement('a');
                                a.href = csvURL;
                                a.download = "result.csv";
                                a.textContent = "Download CSV";
                                a.target = "_blank";

                                // Agregar el enlace al documento
                                $("#downloadCSV").empty().append(a).show();
                            } else {
                                $("#result").html("La respuesta no es un archivo CSV");
                            }
                        },
                        error: function () {
                            $("#result").html(
                                "<div class='alert alert-danger lead'>Error al procesar el archivo CSV</div>");
                        }
                    })

                }
            });

        $("#showQr").click(
            function (event) {
                event.preventDefault();
                $.ajax({
                    type: "GET",
                    url: qrDir,
                    responseType: 'arraybuffer',  // Especifica que la respuesta es un array de bytes
                        success: function (msg, status, request) {
                            // Convierte los bytes a una URL Blob
                        console.log("msg", msg);
                        
                        // Redirigir a la url del QR
                        window.location.href = qrDir;
                        
                    },
                    error: function (xhr, status, error) {
                        console.log("Error en la solicitud:", status, error);
                    }                    
                }
                )
            }

        );
        $("#showHeaders").click(
            function (event) {
                event.preventDefault();
                // Obtiene el último segmento de la URL (el identificador)
                var idFinal = dir.substring(dir.lastIndexOf('/') + 1);
                $.ajax({
                    type: "GET",
                    url: '/api/link/'+ idFinal,
                        success: function (msg, status, request) {
                            // Convierte los bytes a una URL Blob
                        console.log("msg", msg);
                        
                        var summary = msg.info;
                        console.log("Sum .", summary);
                        // Verificar si "sumary" está definido y no es nulo
                        if (summary) {
                            var keys = Object.keys(summary);
                            if (keys.length > 0) {
                                for (var key in summary) {
                                    var summaryList = "Últimos 10 accesos a <strong>" + key + "</strong> realizados por:";
                                    summaryList += "<table class='table table-striped table-bordered'>";
                                    summaryList += "<thead><tr><th>Navegador</th><th>Sistema Operativo</th></tr></thead>";
                                    summaryList += "<tbody>";

                                    if (summary.hasOwnProperty(key)) {
                                        // Iterar sobre los elementos dentro de "67f05a19" (o el nombre de la clave)
                                        var innerList = summary[key];
                                        for (var i = 0; i < innerList.length; i++) {
                                            var item = innerList[i];
                                            if(i < 10) summaryList += "<tr><td>" + item.first + "</td><td>" + item.second + "</td></tr>";
                                        }
                                    }
                                }

                                summaryList += "</tbody></table>";

                                // Ahora puedes agregar summaryList al elemento HTML deseado
                                document.getElementById("userAgent").innerHTML = summaryList;
                            }
                            else{
                                $("#userAgent").html(
                                    "<div>"
                                    + "URL aún sin acceder"
                                    + "</div>");
                            }
                        }
                        
                    },
                    error: function (xhr, status, error) {
                        console.log("Error en la solicitud:", status, error);
                    }                    
                }
                )
            }

        );
        $("#getMetrics").click(
            function (event) {
                event.preventDefault();
                $.ajax({
                    type: "GET",
                    url: '/api/metrics',
                    success: function (data) {
                        console.log("Data:", data);

                        displayMetrics(data)

                    },
                    error: function (xhr, status, error) {
                        console.log("Error en la solicitud:", status, error);
                    }
                });
            }

        );
        function displayMetrics(data) {
            var resultContainer = $("#resultMetrics");
            resultContainer.empty();

            // Crear un contenedor para los botones y aplicar estilos
            var buttonContainer = $("<div class='button-container'></div>");
            resultContainer.append(buttonContainer);

            var metrics = [ "disk.total", "jvm.memory.used", "system.cpu.usage"]
            // Crear botones para cada métrica y agregarlos al contenedor
            for (var i = 0; i < data.names.length; i++) {
                var metricName = data.names[i];
                if (metrics.includes(metricName)) {

                    var button = $("<button class='metric-button'>" + metricName + "</button>");

                    // Agregar un atributo personalizado para almacenar el nombre de la métrica
                    button.attr("data-metric", metricName);

                    // Agregar un salto de línea antes de cada botón
                    //resultContainer.append("<br><br>");

                    // Agregar un botón al contenedor
                    buttonContainer.append(button);
                } else {
                    // console.log(metricName + " no está en el array de nombres.");
                }
            }

            // Agregar un evento de clic a los botones con la clase 'metric-button'
            $(".metric-button").on("click", function () {
                var metricName = $(this).data("metric");

                // Llamar a la función e imprimir el nombre de la métrica
                console.log(metricName);
                    $.ajax({
                     type: "GET",
                     url: '/api/metrics/' + metricName,
                     success: function (metricData) {
                         console.log("Datos de la métrica " + metricName + ":", metricData);
                         // Mostrar los datos en un alert
                         // alert("Datos de la métrica " + metricName + ":\n" + JSON.stringify(metricData));
                         // Dentro de la función success utilizando Bootstrap
                         $("#myModal .modal-body").html("Datos de la métrica " + metricName + ": " + JSON.stringify(metricData));
                         $("#myModal").modal('show');
                     },
                     error: function (xhr, status, error) {
                         console.log("Error en la solicitud:", status, error);
                     }
                 });

            });
        }
    });