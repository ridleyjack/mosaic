# mosaic

This is a project I created in one of my labs. It tries to fit a whole bunch of smaller images to tiles created on a larger image.
Matching is done by taking the average r, g, b of a main image tile and a smaller image and then taking the euclidean distance between 
these two images. A larger distance means the images arn't very similar. I later split the main image tile and smaller images into 
quadrants, then compared the quadrants to get better matches.

<img src="https://github.com/ridleyjack/mosaic/blob/master/rileyMosaic.jpg" alt="pelicans" height="300" width="300">
