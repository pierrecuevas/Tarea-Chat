import path from 'path';
import { fileURLToPath } from 'url';

import CopyWebpackPlugin from 'copy-webpack-plugin'

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export default {
  mode: 'development',
  entry: './index.js',
  output: {
    filename: 'index.js',
    path: path.resolve(__dirname, 'dist'),
    clean: true,
  },
  module: {
    rules: [
      {
        test: /\.css$/i,
        use: ['style-loader', 'css-loader'],
      },
      
    ],
  },
  plugins: [
    new CopyWebpackPlugin({
      patterns: [
        { from: 'extLibs', to: 'extLibs' }, // Copia toda la carpeta
        { from: 'index.html', to: '.' }, // Copia el HTML raíz
        { from: 'index.css', to: '.' }, 
      ],
    }),
  ],
  resolve: {
    fallback: {
      fs: false, // Ignora el módulo 'fs' (file system)
      net: false, // A veces también se necesita para Ice/ZeroC
      tls: false, // A veces también se necesita para Ice/ZeroC
    },
  },
  devServer: {
    static: {
      directory: path.join(__dirname, 'dist'),
    },
    historyApiFallback: true,
    port: 3000,
  },
};
