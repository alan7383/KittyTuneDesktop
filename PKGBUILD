# Maintainer: alan7383 <https://github.com/alan7383/KittyTuneDesktop>
pkgname=kitty-tune-bin
pkgver=1.0.0
pkgrel=1
pkgdesc="KittyTune Desktop Music Player"
arch=('x86_64')
url="https://github.com/alan7383/KittyTuneDesktop"
license=('custom')
depends=('java-runtime>=17' 'hicolor-icon-theme' 'alsa-lib' 'gtk3')
provides=('kitty-tune')
conflicts=('kitty-tune')
source=("https://github.com/alan7383/KittyTuneDesktop/releases/download/v${pkgver}/kitty-tune_${pkgver}-1_amd64.deb")
sha256sums=('SKIP')

package() {
    tar -xf data.tar.* -C "${pkgdir}"
}
