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
                $.ajax({
                    type: "POST",
                    url: "/api/link",
                    data: $(this).serialize(),
                    success: function (msg, status, request) {
                            $("#result").html(
                                "<div class='alert alert-success lead'><a target='_blank' href='"
                                + request.getResponseHeader('Location')
                                + "'>"
                                + request.getResponseHeader('Location')
                                + "</a></div>");
                        
                    },
                    error: function () {
                        $("#result").html(
                            "<div class='alert alert-danger lead'>ERROR</div>");
                    }
                });
            });
    });