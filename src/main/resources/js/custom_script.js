
var errDiv = document.getElementById("error");
var statDiv = document.getElementById("statsTable");


var statRowId = 1;
var ready = false;
var socket = new WebSocket("ws://flag.nemesis/ws");


function removeError() {
    if (errDiv.firstChild != null) { errDiv.removeChild(errDiv.firstChild); }
}

function showError(error) {

    var errMsg = 'Unable to get Queue Status ' + error.code + ':' + error.message;
    color = Math.floor(4);

    $.notify({
        icon: "ti-alert",
        message: errMsg

      },{
          type: "danger",
          timer: 10000,
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
};

socket.onmessage = function(event) {
    var data = JSON.parse(event.data);

    if (data['status'] == 'ok') {
        removeError();

        var status = data['stats'];
        var statRow = document.createElement("tr");
        statRow.classList.add("statsRow");
        statRow.setAttribute("id", "statRow" + statRowId);
        statRowId++;

        if (statRowId > 10) {
            statDiv.removeChild(document.getElementById("statRow" + (statRowId - 10)));
        }

        var statId = document.createElement("td");
        var statProcessing = document.createElement("td");

        var statHigh = document.createElement("td");
        var statNormal = document.createElement("td");
        var statLow = document.createElement("td");

        var statSent = document.createElement("td");
        var statInvalid = document.createElement("td");
        var statTotal = document.createElement("td");

        statId.innerHTML = statRowId;
        statProcessing.innerHTML = status['processing'];

        statHigh.innerHTML = status['queued']['high'];
        statNormal.innerHTML = status['queued']['normal'];
        statLow.innerHTML = status['queued']['low'];

        statSent.innerHTML = status['sent'];
        statInvalid.innerHTML = status['invalid'];
        statTotal.innerHTML = status['total'];

        statRow.appendChild(statId);
        statRow.appendChild(statProcessing);

        statRow.appendChild(statHigh);
        statRow.appendChild(statNormal);
        statRow.appendChild(statLow);

        statRow.appendChild(statSent);
        statRow.appendChild(statInvalid);
        statRow.appendChild(statTotal);

        statDiv.appendChild(statRow);
    }
};

socket.onerror = function(error) {
    showError(error);
};

setInterval(getStatusIfReady, 2000);
