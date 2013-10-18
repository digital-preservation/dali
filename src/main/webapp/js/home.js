var mPendingUnits = [
    /*{interface: 'network', src: 'dri-upload1', label: 'xyz.gpgz', size: '9.2GB', timestamp: 'Fri, 4th October 2013'},
    {interface: 'network', src: 'dri-upload1', label: 'abc.gpgz', size: '1.7GB', timestamp: 'Fri, 4th October 2013'}*/
];

function PendingUnitsCtrl($scope) {
    $scope.pendingUnits = mPendingUnits;
}

function updatePending(fnUpdateModel) {
    var pendingUnitsElem = $("#pendingUnits");
    var scope = angular.element(pendingUnitsElem).scope();

    scope.$apply(function() {
        fnUpdateModel(mPendingUnits);
    });
}

function toHumanSize(bytes) {
  var KB = 1024;
  var MB = Math.pow(KB, 2);
  var GB = Math.pow(KB, 3);
  var TB = Math.pow(KB, 4);

  if(bytes / TB > 1) {
     return Math.ceil(bytes / TB) + " TB";
  } else if(bytes / GB > 1) {
     return Math.ceil(bytes / GB) + " GB";
  } else if(bytes / MB > 1) {
    return Math.ceil(bytes / MB) + " MB";
  } else if(bytes / KB > 1) {
    return Math.ceil(bytes / KB) + " KB";
  } else {
    return bytes + " bytes";
  }
}

function toHumanTime(timestamp) {
   var d = new Date(timestamp)
   return d.toString();
}

$(document).ready(function() {
  "use strict";

  var socket = $.atmosphere;
  var subSocket;
  var transport = 'websocket';

  var request = {
    url: "/unit",
    contentType: "application/json",
    logLevel: 'debug',
    transport: transport,
    fallbackTransport: 'long-polling'
  };

  request.onOpen = function(response) {
    console.log('Atmosphere connected using ' + response.transport);

    transport = response.transport;

    if (response.transport == "local") {
      subSocket.pushLocal("Name?");
    }

    //request initial unit status
    subSocket.push(JSON.stringify({action: 'pending'}));

  };

  request.onReconnect = function(request, response) {
    socket.info("Reconnecting");
    console.log("Reconnecting...");
  };

  request.onMessage = function(response) {
    var message = response.responseBody;
      try {
          var json = JSON.parse(message);

          //is this a pending unit initial status
          if(json.pending) {
            updatePending(function(model) {
                model.length = 0; //empty the model
                $.each(json.pending.unit, function(i, v) {
                    v.size = toHumanSize(v.size);
                    v.timestamp = toHumanTime(v.timestamp);
                    model.push(v); //add to the model
                });
            });

          //is this an addition to the pending units
          } else if(json.pendingAdd) {
            updatePending(function(model) {
                $.each(json.pendingAdd.unit, function(i, v) {
                    v.size = toHumanSize(v.size);
                    v.timestamp = toHumanTime(v.timestamp);
                    model.push(v); //add to the model
                });
            });

          //is this a removal from the pending units
          } else if(json.pendingRemove) {
            updatePending(function(model) {
               $.each(model, function(i, v) {
                $.each(json.pendingRemove.unit, function(y, vv) {
                   if(v.src == vv.src) {
                       model.splice(i, 1);
                   }
                });
               });
            });
          }
      } catch (e) {
          console.log('Error: ', message.data);
          return;
      }

    console.info("Received message: " + json); //TODO should be debug
  };

  request.onClose = function(response) {
    console.log("Connection closed! " = response.responseBody)
  };

  request.onError = function(response) {
    console.log("Error: " + response.responseBody);
  };

  subSocket = socket.subscribe(request);
});