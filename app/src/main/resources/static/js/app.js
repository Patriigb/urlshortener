$(document).ready(
    function () {
        $("#shortener").submit(

            // // Obtén el valor de la casilla de verificación generateQR
            // var generateQR = $("#generateQR").is(":checked");

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

                if (!hayCsv) {
                    $.ajax({
                        type: "POST",
                        url: "/api/link",
                        data: $(this).serialize(),
                        success: function (msg, status, request) {
                            $("#downloadCSV").hide();
                            $("#result").html(
                                "<div class='alert alert-success lead'><a target='_blank' href='"
                                + request.getResponseHeader('Location')
                                + "'>"
                                + request.getResponseHeader('Location')
                                + "</a></div>");
                           // Acceder a la propiedad "sumary" dentro del objeto "properties"
                        var summary = msg.properties.sumary;
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
    });