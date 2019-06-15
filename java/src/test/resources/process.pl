#!/usr/bin/perl
use strict;
use warnings;

while (<>) {
  die $_  if ( ! m/    ([^:]*):(.*)/ );
  my ( $key , $value ) = ($1, $2);
  $key =~ s/\./_/g;
  print "    ".$key.":".$value."\n";
}
