$(function() {
  $('#xy').click(function(e) {
    var offset = $(this).offset();
    var x = e.clientX - offset.left - 7;
    var y = e.clientY - offset.top+$("body").scrollTop() - 13;
    if (x<0) { x=0; }
    if (y<-6) { y=-6; }
    if (x > 284) { x=284; }
    if (y > 278) { y=278; }
    $("#xcoord").val(x);
    $("#ycoord").val(y);
    $("#dest").css("left",x);
    $("#dest").css("top",y);
  });
});
