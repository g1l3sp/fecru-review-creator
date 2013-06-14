

// because the anchor is not working in chrome, this hack

$(function() {
    var urllocation = location.href;
    var anchorIndex = urllocation.indexOf("#");
    if(anchorIndex > -1){
        var anchorText = urllocation.substr(anchorIndex);
        setTimeout(function() {

            // move to the anchor
            $('html, body').animate({
                scrollTop: $(anchorText).offset().top
            }, 100);

            // and draw attention to the feedback message
            $('#feedbackMessage')
                .fadeTo(500,.5).fadeTo(500, 1.0).fadeTo(500,.5).fadeTo(500, 1.0);
        }, 300);
        // alert("dagnabbit2");
    }
});