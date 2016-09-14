var ready = false;
var socket = new WebSocket("ws://flag.nemesis/ws");


function showError(error) {

    var errMsg = 'Unable to get Queue Status ' + error.code + ':' + error.message;

    $.notify({
        icon: "ti-alert",
        message: errMsg
    },{
        type: "danger",
        timer: 10000
    });
}

function getStatusIfReady() {
    if (ready) {
        socket.send(JSON.stringify({"action": "get_stats"}));
    }
}

socket.onopen = function() {
    ready = true;
};

socket.onclose = function(event) {
    ready = false;
    showError(event);
};

socket.onmessage = function(event) {
    var data = JSON.parse(event.data);

    if (data['status'] == 'ok') {

        var stats = data['stats'];
        var queued = stats['queued'];

        $("#stat_queued").text(queued['high'] + "/" + queued['normal'] + "/" + queued['low']);
        $("#stat_processing").text(stats['processing']);
        $("#stat_accepted").text(stats['sent']);
        $("#stat_rejected").text(stats['invalid']);

        var id = $("#stat_flags tr").first().attr("id");

        var flags = data['flags'];
        var arrayId = flags.length - 1;

        for (var i = 0; i < flags.length; i++) {
            if (flags[i]['id'] == id) {
                arrayId = i;
                break;
            }
        }

        for (var j = arrayId - 1; j >= 0; j--) {
            var row = $("<tr></tr>").attr("id", flags[j]['id']);

            row.append($("<td></td>").text(flags[j]['id']));
            row.append($("<td></td>").text(flags[j]['flag']));
            row.append($("<td></td>").text(flags[j]['priority']));
            row.append($("<td></td>").text(flags[j]['state']));
            row.append($("<td></td>").text(flags[j]['time']));

            console.log(row);
            $("#stat_flags").prepend(row);
        }
    }
};

socket.onerror = function(error) {
    showError(error);
};

setInterval(getStatusIfReady, 2000);
